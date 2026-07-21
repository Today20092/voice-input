# Predictive Back for Settings Navigation

## Problem Statement

When a person opens a settings destination such as Model Options, the destination appears with visual feedback. When they use Android's edge-back gesture to return to the settings home screen, the current screen remains frozen throughout the gesture and the home screen appears only after the gesture commits. This feels like a pause and makes back navigation inconsistent with modern Android behavior.

## Solution

Make the shared settings navigation host support Android predictive back. While a person swipes back from any settings destination, the app previews the previous destination with an interactive cross-fade. Committing the gesture completes navigation; cancelling it leaves the current destination visible. Button back and non-gesture system back continue to work.

## User Stories

1. As a settings user, I want the previous screen to appear progressively while I swipe back, so that the app responds immediately to my gesture.
2. As a settings user, I want Model Options to return smoothly to the settings home screen, so that navigation does not appear stalled.
3. As a settings user, I want Language, Input, Theme, Testing, Advanced, Help, Credits, and payment destinations to behave consistently, so that back navigation is predictable throughout settings.
4. As a gesture-navigation user, I want cancelling a partial back swipe to keep me on the current destination, so that exploratory gestures do not lose my place.
5. As a gesture-navigation user, I want committing a back swipe to reveal the correct previous destination, so that the navigation back stack remains trustworthy.
6. As a button-navigation user, I want the Android back button to keep returning to the previous destination, so that predictive-back support does not require gesture navigation.
7. As a user of the in-app back arrow, I want it to keep returning to the previous destination, so that existing navigation controls remain functional.
8. As a user on an older supported Android release, I want settings navigation to continue working, so that the enhancement does not raise the minimum supported OS version.
9. As a user, I want forward navigation to retain clear visual feedback, so that opening a settings destination remains understandable.
10. As a maintainer, I want predictive back configured once at the shared navigation host, so that every settings destination receives the same behavior without page-specific fixes.
11. As a maintainer, I want the compatible Compose navigation stack verified together, so that the dependency upgrade does not introduce runtime or binary incompatibilities.
12. As a maintainer, I want an automated check at the settings navigation boundary and a real-device gesture check, so that both back-stack correctness and interactive behavior are covered.

## Implementation Decisions

- Upgrade Navigation Compose to a stable version that supports predictive in-app back, at least 2.8.0.
- Upgrade only the Compose and AndroidX dependencies required to keep the navigation stack compatible.
- Use Navigation Compose's built-in predictive-back cross-fade at the shared settings navigation host.
- Do not add destination-specific back handlers or a custom animation framework.
- Preserve the existing navigation graph, routes, back-stack behavior, and minimum Android version.
- Apply the behavior to every destination owned by the shared settings navigation host rather than only Model Options.
- Treat a committed gesture, a cancelled gesture, system back, and the in-app back arrow as distinct behaviors that must remain correct.

## Testing Decisions

- Use one instrumentation seam at the shared settings navigation host, the highest existing boundary that covers all settings destinations.
- Verify externally visible behavior: opening a destination changes the route, back returns to the preceding route, and cancelling predictive back retains the current route.
- Do not assert internal animation objects or implementation-specific transition state.
- Perform a real-device or emulator check on Android 15 or newer to confirm that swipe progress visibly cross-fades between destinations.
- Use the supplied screen recording as the before-state reference: the current destination must no longer remain visually frozen until gesture commit.
- Check Model Options explicitly and sample another settings destination to prove the shared behavior is not page-specific.
- Run the existing unit and instrumentation checks affected by the dependency upgrade.

## Out of Scope

- Redesigning settings screens or their navigation graph.
- Adding shared-element, slide, scale, or destination-specific animations.
- Changing model loading, recognition backends, downloads, or Model Options content.
- Migrating to Navigation 3.
- Refactoring every destination to replace its existing `NavController` parameter with callbacks.

## Further Notes

The captured Android recording shows the destination remaining static during the edge gesture and changing immediately on commit. The current app uses Navigation Compose 2.6.0, while predictive in-app back requires Navigation Compose 2.8.0 or newer. Because the app targets Android 15, no manifest opt-in is required for Android 15 and newer.
