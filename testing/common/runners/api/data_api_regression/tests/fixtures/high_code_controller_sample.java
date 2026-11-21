package com.ouroboros.testing.sample;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sys/user")
class UserController {

  @GetMapping
  public String search() {
    return "ok";
  }

  @PostMapping
  public String add() {
    return "ok";
  }

  @DeleteMapping("/{id}")
  public void delete() {
  }
}

@RestController
@RequestMapping("/api/system/info")
class SystemInfoController {

  @GetMapping
  public String getInfo() {
    return "ok";
  }
}
