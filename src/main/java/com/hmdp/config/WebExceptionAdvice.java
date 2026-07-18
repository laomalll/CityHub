package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice // 将当前类标识为异常处理的组件
public class WebExceptionAdvice {

    /**
     * 异常处理handler  @ExceptionHandler(RuntimeException.class)
     * 该注解标记异常处理Handler,当发生指定异常时会调用该方法!
     * @param
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail(e.getMessage());
    }
}
