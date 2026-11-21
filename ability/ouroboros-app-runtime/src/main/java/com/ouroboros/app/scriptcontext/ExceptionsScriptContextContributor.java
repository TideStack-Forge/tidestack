package com.ouroboros.app.scriptcontext;

import java.util.Collections;
import java.util.Map;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.script.ScriptContextContributor;

public class ExceptionsScriptContextContributor implements ScriptContextContributor {
  @Override
  public Map<String, Object> getBindings() {
    return Collections.singletonMap("Exceptions", new ExceptionsBuilder());
  }

  public static class ExceptionsBuilder {
    public void validation(String message) {
      throw new ValidationException(message);
    }

    public void validation(Map<String, String> errors) {
      throw new ValidationException(errors);
    }

    public void business(String message) {
      throw new BusinessException(message);
    }

    public void business(Integer code, String message) {
      throw new BusinessException(code, message);
    }

    public void business(Integer code, String message, Throwable cause) {
      throw new BusinessException(code, message, cause);
    }

    public void throwBusiness(String message) {
      business(message);
    }

    public void throwBusiness(Integer code, String message) {
      business(code, message);
    }

    public void throwBusiness(Integer code, String message, Throwable cause) {
      business(code, message, cause);
    }

    public void internal(String message) {
      throw new InternalException(message);
    }

    public void internal(Integer code, String message) {
      throw new InternalException(code, message);
    }

    public void internal(Integer code, String message, Throwable cause) {
      throw new InternalException(code, message, cause);
    }

    public void throwInternal(String message) {
      internal(message);
    }

    public void throwInternal(Integer code, String message) {
      internal(code, message);
    }

    public void throwInternal(Integer code, String message, Throwable cause) {
      internal(code, message, cause);
    }

    public void platform(String message) {
      throw new PlatformException(message);
    }

    public void platform(Integer code, String message) {
      throw new PlatformException(code, message);
    }

    public void platform(Integer code, String message, Throwable cause) {
      throw new PlatformException(code, message, cause);
    }

    public void throwPlatform(String message) {
      platform(message);
    }

    public void throwPlatform(Integer code, String message) {
      platform(code, message);
    }

    public void throwPlatform(Integer code, String message, Throwable cause) {
      platform(code, message, cause);
    }
  }
}
