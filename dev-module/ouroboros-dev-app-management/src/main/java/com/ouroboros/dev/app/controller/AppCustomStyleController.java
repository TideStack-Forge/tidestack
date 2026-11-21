package com.ouroboros.dev.app.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.web.AbstractSingleFileController;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;

@RestController
@RequestMapping("/api/dev/{appName}/custom-style")
public class AppCustomStyleController extends AbstractSingleFileController<AppCustomStyleController.StyleForm, AppCustomStyleController.StyleForm> {

  @Override
  protected String getSingleFilePath() {
    return "style.css";
  }

  @Override
  protected Try<StyleForm> getInitFormData(VCSFile file) {
    var style = file.getContent();
    var formData = new StyleForm();
    formData.setStyle(style);
    return Try.success(formData);
  }

  @Override
  protected Try<StyleForm> getSubmitModelFromJson(String jsonString) {
    return Json.tryToBean(StyleForm.class, jsonString);
  }

  @Override
  protected Try<String> getPermanentJson(StyleForm model) {
    return Try.success(model.getStyle());
  }

  public static class StyleForm {
    private String style;

    public String getStyle() {
      return style;
    }

    public void setStyle(String style) {
      this.style = style;
    }
  }
}
