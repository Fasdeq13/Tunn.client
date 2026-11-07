#ifndef FQ_UI_H
#define FQ_UI_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

#define FQ_UI_ABI 1

#define FQ_TOUCH_DOWN 0
#define FQ_TOUCH_MOVE 1
#define FQ_TOUCH_UP 2
#define FQ_TOUCH_CANCEL 3

int fq_ui_abi(void);
const char *fq_ui_info(void);
int fq_ui_init(int width, int height, float density);
void fq_ui_resize(int width, int height);
void fq_ui_state(const char *json);
void fq_ui_touch(int kind, float x, float y);
void fq_ui_back(void);
int fq_ui_render(uint8_t *rgba, int width, int height, int stride_bytes, uint32_t time_ms);
const char *fq_ui_poll(void);
void fq_ui_close(void);

#ifdef __cplusplus
}
#endif
#endif
