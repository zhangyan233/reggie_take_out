package com.itheima.reggie.utils;

/**
 * 自定义异常处理类
 */
public class CustomException extends RuntimeException{
    public CustomException (String message){
        super(message);
    }
}
