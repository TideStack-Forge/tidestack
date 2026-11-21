package com.ouroboros.workflow.approval.manage.service.impl;

import org.springframework.stereotype.Service;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.workflow.approval.manage.entity.WorkflowCategory;
import com.ouroboros.workflow.approval.manage.service.ModelCategoryService;

@Service
public class ModelCategoryServiceImpl extends SimpleModelServiceImpl<String, WorkflowCategory> implements ModelCategoryService {
}
