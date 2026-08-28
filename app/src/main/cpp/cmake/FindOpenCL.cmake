set(OpenCL_FOUND TRUE)
set(OpenCL_VERSION_STRING "3.0")
set(OpenCL_INCLUDE_DIRS "${S1_OPENCL_HEADERS_DIR}")
set(OpenCL_LIBRARIES OpenCL)

if(NOT TARGET OpenCL::OpenCL)
    add_library(OpenCL::OpenCL ALIAS OpenCL)
endif()
