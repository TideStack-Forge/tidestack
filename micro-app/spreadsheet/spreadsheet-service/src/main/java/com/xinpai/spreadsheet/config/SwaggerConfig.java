/*
 * @Author: Song Mingxu
 * @Date: 2022-05-05 23:35:53
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-15 01:35:42
 * @Description: file content
 */
package com.xinpai.spreadsheet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

// @Configuration
// @EnableSwagger2
// @EnableKnife4j
public class SwaggerConfig {
    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.withClassAnnotation(RestController.class))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                // 页面标题
                .title("Spreadsheet API")
                // 创建人
                .contact(new Contact("Song Mingxu", "", "song.mingxu@foxmail.com"))
                // 版本号
                .version("1.0")
                // 描述
                .description("Spreadsheet API文档")
                .build();
    }
}