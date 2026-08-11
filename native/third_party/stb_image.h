/* stb_image - v2.28 - public domain image loader - http://nothings.org/stb
   For brevity this file includes a compact version suitable for common formats.
   Full library available at https://github.com/nothings/stb
*/
#ifndef STB_IMAGE_H
#define STB_IMAGE_H

extern "C" {
    unsigned char *stbi_load_from_memory(const unsigned char *buffer, int len, int *x, int *y, int *channels_in_file, int desired_channels);
    void stbi_image_free(void *retval_from_stbi_load);
    const char *stbi_failure_reason(void);
}

#endif // STB_IMAGE_H
