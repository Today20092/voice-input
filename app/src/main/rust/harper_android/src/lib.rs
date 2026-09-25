//! Conservative automatic fixes for final English dictation. No spelling or rewriting.
use harper_core::linting::{Lint, LintGroup};
use harper_core::spell::{FstDictionary, MergedDictionary, MutableDictionary};
use harper_core::{Dialect, DictWordMetadata, Document};
use serde::Serialize;
use std::ffi::{CString, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::{Arc, Once};

const MAX_CHARS: usize = 10_000;
const RULES: &[&str] = &[
    "Spaces", "NoFrenchSpaces", "MissingSpace", "CommaFixes",
    "CapitalizePersonalPronouns", "SentenceCapitalization",
];

#[derive(Serialize)]
struct Cleaned {
    text: String,
    edits: usize,
}

fn preferred_terms(vocabulary: &str) -> Vec<String> {
    vocabulary.split([',', '\n', '\r'])
        .flat_map(|entry| entry.split("=>"))
        .map(str::trim).filter(|term| !term.is_empty())
        .take(2_000).map(str::to_owned).collect()
}

// Offsets remain Unicode scalar indices in Rust. Never expose them as JVM indices.
fn protected_ranges(chars: &[char], terms: &[String]) -> Vec<(usize, usize)> {
    let mut ranges = Vec::new();
    let mut start = 0;
    while start < chars.len() {
        if chars[start].is_whitespace() { start += 1; continue; }
        let mut end = start + 1;
        while end < chars.len() && !chars[end].is_whitespace() { end += 1; }
        let token: String = chars[start..end].iter().collect();
        // Preserve addresses, numbers, paths, identifiers and markup verbatim.
        if token.chars().any(|c| c.is_numeric() || "@/\\_`{}<>=#".contains(c))
            || token.starts_with("www.")
            || (token.chars().all(|c| c.is_alphabetic() || c == '\'')
                && token.chars().skip(1).any(char::is_uppercase))
        {
            ranges.push((start, end));
        }
        start = end;
    }
    // Protect whole inline/fenced code sections, including spaces inside them.
    let mut code_start = None;
    for (index, &ch) in chars.iter().enumerate() {
        if ch == '`' {
            if let Some(begin) = code_start.take() { ranges.push((begin, index + 1)); }
            else { code_start = Some(index); }
        }
    }
    if let Some(begin) = code_start { ranges.push((begin, chars.len())); }
    for term in terms {
        let needle: Vec<char> = term.chars().collect();
        if needle.is_empty() || needle.len() > chars.len() { continue; }
        for (index, window) in chars.windows(needle.len()).enumerate() {
            let end = index + needle.len();
            let word_char = |c: char| c.is_alphanumeric() || c == '\'' || c == '’';
            if (index == 0 || !word_char(chars[index - 1]))
                && (end == chars.len() || !word_char(chars[end]))
                && window.iter().zip(&needle).all(|(a, b)| a.to_lowercase().eq(b.to_lowercase())) {
                ranges.push((index, index + needle.len()));
            }
        }
    }
    ranges
}

fn safe_edit(lint: &Lint, rule: &str, chars: &[char], protected: &[(usize, usize)]) -> bool {
    let span = lint.span;
    if lint.suggestions.len() != 1 || span.start >= span.end || span.end > chars.len() {
        return false;
    }
    if protected.iter().any(|&(start, end)| span.start < end && span.end > start) {
        return false;
    }
    let source = &chars[span.start..span.end];
    if source.iter().all(|&c| c == ' ') {
        let line_start = chars[..span.start].iter().rposition(|&c| c == '\n').map_or(0, |i| i + 1);
        if chars[line_start..span.start].iter().all(|c| c.is_whitespace()) { return false; }
    }
    // No line-break/indentation changes, East Asian punctuation replacement, or "ive" -> "Ive".
    if source.iter().any(|c| matches!(c, '\n' | '\r' | '\t' | '、' | '，')) { return false; }
    if rule == "CapitalizePersonalPronouns" && source == ['i', 'v', 'e'] { return false; }
    true
}

fn clean(text: &str, vocabulary: &str) -> Cleaned {
    let mut chars: Vec<char> = text.chars().collect();
    if chars.len() > MAX_CHARS || vocabulary.len() > 100_000 {
        return Cleaned { text: text.to_owned(), edits: 0 };
    }
    let terms = preferred_terms(vocabulary);
    let mut personal = MutableDictionary::new();
    for term in &terms {
        for word in term.split_whitespace() {
            personal.append_word_str(word, DictWordMetadata::default());
        }
    }
    let mut dictionary = MergedDictionary::new();
    dictionary.add_dictionary(Arc::new(personal));
    dictionary.add_dictionary(FstDictionary::curated());
    let dictionary = Arc::new(dictionary);
    let mut group = LintGroup::new_curated_empty_config(dictionary.clone(), Dialect::American);
    // Explicitly disable ALL rules, including ones enabled by default in future upstream changes.
    group.set_all_rules_to(Some(false));
    for rule in RULES { group.config.set_rule_enabled(*rule, true); }

    let mut total = 0;
    // Reparse once after spacing repairs so dependent capitalization can be applied.
    // Strictly bounded; never iterate until convergence.
    for _ in 0..2 {
        let current: String = chars.iter().collect();
        let document = Document::new_plain_english(&current, dictionary.as_ref());
        let protected = protected_ranges(&chars, &terms);
        let mut lints: Vec<Lint> = group.organized_lints(&document).into_iter()
            .filter(|(rule, _)| RULES.contains(&rule.as_str()))
            .flat_map(|(rule, lints)| lints.into_iter().filter_map(|lint| {
                safe_edit(&lint, &rule, &chars, &protected).then_some(lint)
            }).collect::<Vec<_>>()).collect();
        // Stable ordering resolves overlaps deterministically, independently of hash iteration.
        lints.sort_by_key(|lint| (lint.span.start, lint.span.end, lint.priority));
        let mut accepted = Vec::new();
        let mut previous_end = 0;
        for lint in lints {
            if lint.span.start >= previous_end {
                previous_end = lint.span.end;
                accepted.push(lint);
            }
        }
        if accepted.is_empty() { break; }
        total += accepted.len();
        for lint in accepted.into_iter().rev() { lint.suggestions[0].apply(lint.span, &mut chars); }
    }
    Cleaned { text: chars.iter().collect(), edits: total }
}

/// Inputs are UTF-8 byte buffers owned by JNI for the duration of this call.
/// Returns an owned JSON C string; the caller must release it with harper_free.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn harper_clean(
    text: *const u8, text_len: usize, vocabulary: *const u8, vocabulary_len: usize,
) -> *mut c_char {
    static HOOK: Once = Once::new();
    // Rust's default panic hook can print user text. The boundary returns failure without logging it.
    HOOK.call_once(|| std::panic::set_hook(Box::new(|_| {})));
    if text.is_null() || vocabulary.is_null() || text_len > 40_000 || vocabulary_len > 100_000 {
        return std::ptr::null_mut();
    }
    catch_unwind(AssertUnwindSafe(|| {
        let text = std::str::from_utf8(unsafe { std::slice::from_raw_parts(text, text_len) }).ok()?;
        let vocabulary = std::str::from_utf8(unsafe { std::slice::from_raw_parts(vocabulary, vocabulary_len) }).ok()?;
        let result = serde_json::to_string(&clean(text, vocabulary)).ok()?;
        CString::new(result).ok().map(CString::into_raw)
    })).ok().flatten().unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn harper_free(result: *mut c_char) {
    if !result.is_null() { drop(unsafe { CString::from_raw(result) }); }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fixes_pronouns_comma_spacing_and_double_spaces() {
        assert_eq!(clean("i think we should go , but  i'm tired.", "").text,
            "I think we should go, but I'm tired.");
    }

    #[test]
    fn sentence_spacing_and_capitalization() {
        assert_eq!(clean("the kettle hissed on the stove.A thin ribbon of steam curled toward the ceiling.", "").text,
            "The kettle hissed on the stove. A thin ribbon of steam curled toward the ceiling.");
    }

    #[test]
    fn keeps_words_numbers_negation_and_repetition() {
        for input in ["I had had enough.", "I really really like this.", "Please do not send $1,000.50 on 12/03/2026.",
            "Your the man.", "This is a error.", "Call Haithum about Orukeet.", "ive", "Hello， world"] {
            assert_eq!(clean(input, "Haithum, Orukeet").text, input);
        }
    }

    #[test]
    fn protects_addresses_code_and_personal_spellings() {
        for input in ["Email i@example.com or visit https://example.com/a,b.", "Use `i  =  i+1` here.",
            "iFixit is a company that repairs phones.", "Try foo_bar and eBay today."] {
            assert_eq!(clean(input, "iFixit, eBay").text, input);
        }
    }

    #[test]
    fn short_personal_terms_do_not_protect_unrelated_words() {
        assert_eq!(clean("the kettle hissed on the stove.", "he").text,
            "The kettle hissed on the stove.");
    }

    #[test]
    fn handles_non_bmp_and_combining_characters_without_offset_corruption() {
        let input = "😀 i think cafe\u{301} is nice , but i'm tired.";
        let result = clean(input, "cafe\u{301}");
        assert_eq!(result.text, "😀 I think cafe\u{301} is nice, but I'm tired.");
        assert_eq!(clean(&result.text, "cafe\u{301}").text, result.text);
    }

    #[test]
    fn preserves_layout_and_limits_work() {
        let input = "Hello\n\n    world\tthere";
        assert_eq!(clean(input, "").text, input);
        let long = "i ".repeat(6_000);
        assert_eq!(clean(&long, "").text, long);
        assert_eq!(clean("", "").text, "");
    }

    #[test]
    fn ffi_round_trip_and_invalid_utf8() {
        let input = "😀 i am here";
        let dictionary = b"";
        unsafe {
            let output = harper_clean(input.as_ptr(), input.len(), dictionary.as_ptr(), 0);
            assert!(!output.is_null());
            let json = std::ffi::CStr::from_ptr(output).to_str().unwrap();
            assert!(json.contains("😀 I am here"));
            harper_free(output);
            assert!(harper_clean([255u8].as_ptr(), 1, dictionary.as_ptr(), 0).is_null());
        }
    }
}
