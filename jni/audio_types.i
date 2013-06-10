/* File: audio_types.i */
/* Boilerplate pointer and array management for audio libs */

/* Create methods to make arrays in java */
%include carrays.i
%array_functions(short, shortArray)
%array_functions(int, intArray)
%array_functions(float, floatArray)

/* use Java byte[] instead of String for char* */
%import various.i

%apply char *BYTE { char * }

/* Wrap to pointers */
%include "typemaps.i"
%apply int *INOUT { int * }
%apply int *INOUT { unsigned int * }
%apply short *INOUT { short * }
%apply float *INOUT { float * }

%{

void *intToVoidPointer(int *intValue) {ndk
    return (void *)intValue;
}

%}

extern void *intToVoidPointer(int *intValue);