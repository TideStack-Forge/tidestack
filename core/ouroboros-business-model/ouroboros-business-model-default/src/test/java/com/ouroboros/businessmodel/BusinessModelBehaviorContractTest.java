package com.ouroboros.businessmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventReference;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.custom.CustomAPIMPS;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.util.Json;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIModel;

class BusinessModelBehaviorContractTest {
  @Test
  void identityAliasesMustResolveToADeclaredObjectRoleBeforePublication() {
    for (String pointer : List.of("/params/record/id", "params/record/id", "/context/actor/id", "/params/missing/id", "/params/record/name")) {
      var source = meta("sales", "Order");
      var action = action("submit", "sales.Order", null);
      action.setInputSchema(Map.of("type", "object", "properties", Map.of(
          "params", Map.of("type", "object", "properties", Map.of("record", Map.of("type", "object",
              "x-business-object-ref", "sales.Order", "properties", Map.of("id", Map.of("type", "string"))))),
          "data", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string", "x-business-object-identity", pointer))))));
      source.setActions(List.of(action));
      String errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
      assertEquals(!pointer.equals("/params/record/id"), errors.contains("x-business-object-identity-invalid"), errors);
    }
  }

  @Test
  void draftFourIgnoresResourceIdsAlongsideReferencesForActionAndEventObjectRoots() {
    for (String idKeyword : List.of("$id", "id")) {
      for (boolean acceptsObject : List.of(false, true)) {
        var schema = Map.<String, Object>of("$schema", "http://json-schema.org/draft-04/schema#",
            "definitions", Map.of("value", Map.of("type", acceptsObject ? "object" : "string")),
            "allOf", List.of(Map.of(idKeyword, "https://example.test/nested.json", "$ref", "#/definitions/value",
                "definitions", Map.of("value", Map.of("type", acceptsObject ? "string" : "object")))));
        assertTrue(com.ouroboros.util.JsonSchemas.isValidSchema(schema), schema.toString());
        assertEquals(acceptsObject, com.ouroboros.util.JsonSchemas.validate(schema, Map.of()), schema.toString());
        var source = meta("sales", "Order");
        var action = action("submit", "sales.Order", null);
        action.setInputSchema(schema);
        var event = event("submitted");
        event.setPayloadSchema(schema);
        source.setActions(List.of(action));
        source.setEvents(List.of(event));
        var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
        assertEquals(acceptsObject, validation.isValid(), validation.getErrors().toString());
        if (!acceptsObject) {
          assertTrue(validation.getErrors().toString().contains("actions.submit.inputSchema-requires-object"));
          assertTrue(validation.getErrors().toString().contains("events.submitted.payloadSchema-requires-object"));
        }
      }
    }
  }

  @Test
  void actionInputsAndEventPayloadsRejectNonObjectRootSchemas() {
    for (var schema : List.<Map<String, Object>>of(Map.of("type", "string"), Map.of("type", "array"),
        Map.of("type", List.of("string", "null")), Map.of("allOf", List.of(Map.of("type", "string"))),
        Map.of("const", "value"), Map.of("enum", List.of("value", 1)),
        Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "array"))),
        Map.of("oneOf", List.of(Map.of("type", "string"), Map.of("type", "array"))),
        Map.of("not", Map.of("type", "object")), Map.of("allOf", List.of(false)),
        Map.of("$ref", "#/definitions/text", "definitions", Map.of("text", Map.of("type", "string"))),
        Map.of("not", Map.of("type", "object", "title", "Excluded objects")), Map.of("not", Map.of()),
        Map.of("not", Map.of("required", List.of(), "additionalProperties", true, "minProperties", 0)),
        Map.of("not", Map.of("$ref", "#/$defs/object", "$defs", Map.of("object", Map.of("type", "object"))),
            "$defs", Map.of("object", Map.of("type", "object"))),
        Map.of("not", Map.of("anyOf", List.of(Map.of("type", "object"), Map.of("type", "string")))),
        Map.of("oneOf", List.of(true, Map.of("type", "object"))),
        Map.of("if", Map.of("type", "object"), "then", Map.of("type", "string")),
        Map.of("if", Map.of("type", "string"), "else", false),
        Map.of("$schema", "https://json-schema.org/draft/2020-12/schema", "$ref", "#/$defs/obj",
            "$defs", Map.of("obj", Map.of("type", "object")), "type", "string"),
        Map.of("$ref", "#/definitions/text%20value", "definitions", Map.of("text value", Map.of("type", "string"))),
        Map.of("$ref", "#%2Fdefinitions%2Ftext", "definitions", Map.of("text", Map.of("type", "string"))))) {
      var source = meta("sales", "Order");
      var action = action("submit", "sales.Order", null);
      action.setInputSchema(schema);
      var event = event("submitted");
      event.setPayloadSchema(schema);
      source.setActions(List.of(action));
      source.setEvents(List.of(event));
      var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
      assertFalse(validation.isValid(), schema.toString());
      String errors = validation.getErrors().toString();
      assertTrue(errors.contains("actions.submit.inputSchema-requires-object"), errors);
      assertTrue(errors.contains("events.submitted.payloadSchema-requires-object"), errors);
    }
  }

  @Test
  void objectRootSchemasKeepOpenNullableAndComposedObjectContracts() {
    for (var schema : List.<Map<String, Object>>of(Map.of(), Map.of("type", "object"),
        Map.of("type", List.of("object", "null")),
        Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "object"))),
        Map.of("oneOf", List.of(Map.of("type", "string"), Map.of("type", "object"))),
        Map.of("allOf", List.of(true, Map.of("type", "object"))),
        Map.of("const", Map.of("id", "one")), Map.of("enum", List.of("value", Map.of("id", "one"))),
        Map.of("not", Map.of("type", "string")),
        Map.of("$ref", "#/$defs/object", "$defs", Map.of("object", Map.of("type", "object"))),
        Map.of("not", Map.of("not", Map.of("type", "object"))),
        Map.of("not", Map.of("type", "object", "required", List.of("id"))),
        Map.of("not", Map.of("const", "text")),
        Map.of("$ref", "#/definitions/obj", "definitions", Map.of("obj", Map.of("type", "object")), "type", "string"),
        Map.of("oneOf", List.of(Map.of("required", List.of("id")), Map.of("type", "string"))),
        Map.of("if", Map.of("required", List.of("id")), "then", Map.of("type", "object"), "else", Map.of("type", "object")),
        Map.of("if", Map.of("required", List.of("id")), "then", Map.of("type", "object"), "else", false),
        Map.of("$schema", "http://json-schema.org/draft-04/schema#", "type", "object", "const", "ignored"),
        Map.of("$schema", "http://json-schema.org/draft-04/schema#", "type", "object", "if", true, "then", Map.of("type", "string")))) {
      var source = meta("sales", "Order");
      var action = action("submit", "sales.Order", null);
      action.setInputSchema(schema);
      action.setOutputSchema(Map.of("type", "string"));
      var event = event("submitted");
      event.setPayloadSchema(schema);
      source.setActions(List.of(action));
      source.setEvents(List.of(event));
      var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
      assertTrue(validation.isValid(), validation.getErrors().toString());
    }
  }

  @Test
  void listenerConfigurationBelongsToOnePublishedSubscriptionAndChangesItsRevision() {
    var source = meta("sales", "Order"); source.setEvents(List.of(event("submitted")));
    var subscription = subscription("observe", "sales.Order", "submitted", null, null);
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("observeListener");
    var options = new java.util.LinkedHashMap<String, Object>(); options.put("channel", "email");
    subscription.getTarget().setConfig(Map.of("options", options)); source.setEventSubscriptions(List.of(subscription));
    var original = new DefaultBusinessModel(source, emptyProvider()).getBehavior();
    options.put("channel", "sms");
    assertEquals(Map.of("options", Map.of("channel", "email")), original.getEventSubscriptions().get("observe").getTarget().getConfig());
    var revised = new DefaultBusinessModel(source, emptyProvider()).getBehavior();
    assertNotEquals(original.getModelRevision(), revised.getModelRevision());
    ((Map<?, ?>) original.getEventSubscriptions().get("observe").getTarget().getConfig().get("options")).clear();
    assertEquals(Map.of("options", Map.of("channel", "email")), original.getEventSubscriptions().get("observe").getTarget().getConfig());
    var errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertFalse(errors.contains("listenerConfig-requires-listener"), errors);
    subscription.getTarget().setInputMapping(Map.of("id", "${ event.payload.id }"));
    var mapped = new DefaultBusinessModel(source, emptyProvider()).getBehavior();
    assertNotEquals(revised.getModelRevision(), mapped.getModelRevision());
    assertEquals(Map.of("id", "${ event.payload.id }"), mapped.getEventSubscriptions().get("observe").getTarget().getInputMapping());
    subscription.getTarget().setInputMapping(Map.of("data", "${event.payload}"));
    errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertFalse(errors.contains("inputMapping-requires-action"), errors);
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null); subscription.getTarget().setModelFullName("sales.Order"); subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("submit");
    errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertTrue(errors.contains("eventSubscriptions.observe.target-exclusive"), errors);
    subscription.getTarget().setConfig(null);
    subscription.getTarget().setInputMapping(Map.of("id", "${ event.payload.id }"));
    errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertFalse(errors.contains("listenerInputMapping-requires-listener"), errors);
  }

  @Test
  void subscriptionFiltersRejectMethodAndTypeAccessBeforePublication() {
    var source = meta("sales", "Order");
    source.setEvents(List.of(event("submitted")));
    var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
    subscription.setName("observe");
    subscription.getSourceEvent().setModelFullName("sales.Order");
    subscription.getSourceEvent().setEventName("submitted");
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("observeListener");
    subscription.setFilter("${false and T(java.lang.System).setProperty('probe','changed') == null}");
    source.setEventSubscriptions(List.of(subscription));
    var errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertTrue(errors.contains("eventSubscriptions.observe.filter-invalid"), errors);
    subscription.setFilter("${event.payload.orderId == 'order-1'}");
    errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertFalse(errors.contains("eventSubscriptions.observe.filter-invalid"), errors);
  }

  @Test
  void dynamicActionsCannotReuseReservedEventOperationsPermissions() {
    var source = meta("businessModel", "events");
    source.setActions(List.of(action("inspect", "businessModel.events", null), action("redrive", "businessModel.events", null)));
    var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
    assertFalse(validation.isValid());
    assertTrue(validation.getErrors().toString().contains("actions.inspect.reserved-operation-authority-conflict"));
    assertTrue(validation.getErrors().toString().contains("actions.redrive.reserved-operation-authority-conflict"));
  }
  @Test
  void relationMarkersMustResolveCanonicalMembersAndCannotDescribeEventPayloads() {
    var source = meta("sales", "Order");
    var relationMarker = Map.<String, Object>of("x-business-relation", Map.of(
        "modelFullName", "sales.Order", "relationName", "missingRelation"));
    var submit = action("submit", "sales.Order", null);
    submit.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of("type", "object", "properties", Map.of("customer", relationMarker)))));
    source.setActions(List.of(submit));
    var submitted = event("submitted");
    submitted.setPayloadSchema(Map.of("type", "object", "properties", Map.of("customer", relationMarker)));
    source.setEvents(List.of(submitted));
    var errors = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertTrue(errors.contains("actions.submit.inputSchema.properties.data.properties.customer.x-business-relation-relation-unavailable"), errors);
    assertTrue(errors.contains("events.submitted.payloadSchema.properties.customer.x-business-relation-invalid-action-relation"), errors);

    submit.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of("allOf", List.of(Map.of("properties", Map.of("customer", relationMarker)))))));
    var unsupported = new DefaultBusinessModel(source, emptyProvider()).getValidationResult().getErrors().toString();
    assertTrue(unsupported.contains("x-business-relation.object-reference-layout-unsupported"), unsupported);
  }
  @Test
  void publicationRejectsUnresolvableObjectReferenceLocationsForActionsAndEvents() {
    var customerMeta = meta("sales", "Customer");
    var id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("String");
    id.setIsPrimaryKey(true);
    customerMeta.setFields(List.of(id));
    var customer = new DefaultBusinessModel(customerMeta, emptyProvider());
    var reference = Map.of("type", "object", "x-business-object-ref", "sales.Customer");
    var unsupported = Map.<String, Object>of("allOf", List.of(Map.of("type", "object", "properties",
        Map.of("customer", reference))));
    var source = meta("sales", "Order");
    var submit = action("submit", "sales.Order", null);
    submit.setInputSchema(Map.of("type", "object", "properties", Map.of("data", unsupported)));
    source.setActions(List.of(submit));
    var submitted = event("submitted");
    submitted.setPayloadSchema(unsupported);
    source.setEvents(List.of(submitted));
    var validation = new DefaultBusinessModel(source, provider(Map.of("sales.Customer", customer))).getValidationResult();
    assertFalse(validation.isValid());
    assertTrue(validation.getErrors().toString().contains(
        "actions.submit.inputSchema.properties.data.allOf[0].properties.customer.x-business-object-ref.object-reference-layout-unsupported"));
    assertTrue(validation.getErrors().toString().contains(
        "events.submitted.payloadSchema.allOf[0].properties.customer.x-business-object-ref.object-reference-layout-unsupported"));
  }


  @Test
  void actionAndEventAuthoritiesMustHaveDistinctNames() {
    var source = meta("sales", "Order");
    source.setEvents(List.of(event("submitted")));
    source.setActions(List.of(action("events.submitted.read", "sales.Order", null),
        action("EVENTS.submitted.EMIT", "sales.Order", null)));
    var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
    assertFalse(validation.isValid());
    assertTrue(validation.getErrors().toString().contains("actions.events.submitted.read.event-authority-conflict"));
    assertTrue(validation.getErrors().toString().contains("actions.EVENTS.submitted.EMIT.event-authority-conflict"));
  }

  @Test
  void publishedBehaviorRevisionTracksTheCompleteDynamicModelSnapshot() {
    DefaultBusinessModelMeta firstMeta = meta("sales", "Order");
    firstMeta.setActions(List.of(action("submit", "sales.Order", null)));
    DefaultBusinessModelMeta secondMeta = meta("sales", "Order");
    secondMeta.setTitle("Changed order model");
    secondMeta.setActions(List.of(action("submit", "sales.Order", null)));

    String firstRevision = new DefaultBusinessModel(firstMeta, emptyProvider()).getBehavior().getModelRevision();
    String secondRevision = new DefaultBusinessModel(secondMeta, emptyProvider()).getBehavior().getModelRevision();

    assertEquals(64, firstRevision.length());
    assertNotEquals(firstRevision, secondRevision);
  }

  @Test
  void shouldValidateDeclaredObjectReferenceModelAndPrimaryKeyAtPublication() {
    DefaultBusinessModelMeta customerMeta = meta("sales", "Customer");
    BusinessModelFieldMeta customerId = new BusinessModelFieldMeta();
    customerId.setName("id");
    customerId.setType("Number");
    customerId.setIsPrimaryKey(true);
    customerMeta.setFields(List.of(customerId));
    DefaultBusinessModel customerModel = new DefaultBusinessModel(customerMeta, emptyProvider());

    DefaultBusinessModelMeta orderMeta = meta("sales", "Order");
    orderMeta.setActions(List.of(actionWithObjectReference("submit", "sales.Customer")));
    DefaultBusinessModel orderModel = new DefaultBusinessModel(orderMeta,
        provider(Map.of(customerModel.getFullName(), customerModel)));

    assertTrue(orderModel.getValidationResult().isValid(), orderModel.getValidationResult().getErrors().toString());
  }

  @Test
  void shouldRejectDeclaredObjectReferenceWithMissingModelOrInvalidPrimaryKey() {
    DefaultBusinessModelMeta orderMeta = meta("sales", "Order");
    orderMeta.setActions(List.of(actionWithObjectReference("missingCustomer", "sales.MissingCustomer")));
    ValidationResult missingModelResult = new DefaultBusinessModel(orderMeta, emptyProvider()).getValidationResult();
    assertFalse(missingModelResult.isValid());
    assertTrue(missingModelResult.getErrors().toString()
        .contains("actions.missingCustomer.inputSchema.properties.data.properties.customer.items.x-business-object-ref-model-not-found"));

    DefaultBusinessModelMeta customerMeta = meta("sales", "Customer");
    BusinessModelFieldMeta customerKey = new BusinessModelFieldMeta();
    customerKey.setName("customerId");
    customerKey.setType("Number");
    customerKey.setIsPrimaryKey(true);
    customerMeta.setFields(List.of(customerKey));
    DefaultBusinessModel customerModel = new DefaultBusinessModel(customerMeta, emptyProvider());

    DefaultBusinessModelMeta orderWithInvalidTargetMeta = meta("sales", "OrderWithInvalidTarget");
    orderWithInvalidTargetMeta.setActions(List.of(actionWithObjectReference("submit", "sales.Customer")));
    DefaultBusinessModel orderWithInvalidTarget = new DefaultBusinessModel(orderWithInvalidTargetMeta,
        provider(Map.of(customerModel.getFullName(), customerModel)));
    ValidationResult invalidPrimaryKeyResult = orderWithInvalidTarget.getValidationResult();

    assertFalse(invalidPrimaryKeyResult.isValid());
    assertTrue(invalidPrimaryKeyResult.getErrors().toString()
        .contains("actions.submit.inputSchema.properties.data.properties.customer.items.x-business-object-ref-requires-single-id-primary-key"));
  }

  @Test
  void shouldRoundTripDynamicDefinitionsAndProjectTheSameBehaviorContract() {
    DefaultBusinessModelMeta source = meta("sales", "Order");
    source.setActions(List.of(action("submit", "sales.Order", "submitted")));
    source.setEvents(List.of(event("submitted")));
    source.setEventSubscriptions(List.of(subscription(
        "createReservation", "sales.Order", "submitted", "sales.Order", "submit")));

    DefaultBusinessModelMeta roundTripped = Json.tryToBean(DefaultBusinessModelMeta.class, Json.toJsonString(source)).get();
    DefaultBusinessModel model = new DefaultBusinessModel(roundTripped, emptyProvider());

    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    ModelBundle bundle = model.buildModelBundle();
    BusinessModelBehavior behavior = bundle.getModel(BusinessModelBehavior.class, "sales.Order").orElseThrow();

    assertEquals("submit", behavior.getActions().get("submit").getName());
    assertEquals("submitted", behavior.getEvents().get("submitted").getName());
    assertEquals("createReservation", behavior.getEventSubscriptions().get("createReservation").getName());
    assertEquals("submit", bundle.getModel(Authority.class, "sales.Order.submit").orElseThrow().getName());
    assertTrue(bundle.getModel(Authority.class, "sales.Order.events.submitted.emit").isPresent());
    assertTrue(bundle.getModel(Authority.class, "sales.Order.events.submitted.read").isPresent());
  }

  @Test
  void actionAuthorityGrantsItsInvocationWithoutGrantingOtherActions() {
    DefaultBusinessModelMeta source = meta("sales", "Order");
    source.setActions(List.of(action("submit", "sales.Order", null), action("cancel", "sales.Order", null)));
    ModelBundle bundle = new DefaultBusinessModel(source, emptyProvider()).buildModelBundle();
    Authority submit = bundle.getModel(Authority.class, "sales.Order.submit").orElseThrow();
    String base = "/api/business-model-behaviors/sales.Order/actions";

    assertTrue(matches(submit, "GET", base));
    assertTrue(matches(submit, "POST", base + "/submit/availability"));
    assertTrue(matches(submit, "POST", base + "/submit/invoke"));
    assertFalse(matches(submit, "POST", base + "/cancel/invoke"));
    assertFalse(matches(submit, "POST", base + "/cancel/availability"));
    assertFalse(matches(submit, "POST", "/api/business-model-behaviors/sales.Other/actions/submit/invoke"));
  }

  private static boolean matches(Authority authority, String method, String path) {
    return authority.getApis().stream().anyMatch(api -> api.startsWith(method + " ")
        && new PathPatternParser().parse(api.substring(api.indexOf(' ') + 1)).matches(PathContainer.parsePath(path)));
  }

  @Test
  void shouldApplyRegisteredBindingToDeclaredDynamicActionInPublishedSnapshot() {
    DefaultBusinessModelMeta source = meta("sales", "AnnotatedOrder");
    ActionDefinition action = action("submit", "sales.AnnotatedOrder", null);
    action.setBinding(null);
    source.setActions(List.of(action));
    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());

    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    ActionDefinition published = model.buildModelBundle()
        .getModel(BusinessModelBehavior.class, "sales.AnnotatedOrder")
        .orElseThrow()
        .getActions().get("submit");

    assertEquals("handler", published.getBinding().getKind());
    assertEquals("annotated-handler", published.getBinding().getHandlerName());
    assertNull(source.getActions().get(0).getBinding());
  }

  @Test
  void shouldNormalizeLegacyCrudAndCustomApiSettingsIntoActions() {
    DefaultBusinessModelMeta source = meta("sales", "LegacyOrder");
    BusinessModelFieldMeta id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("Number");
    id.setIsPrimaryKey(true);
    source.setFields(List.of(id));
    source.setExtraProp("apis", Map.of(
        "add", Map.of("isEnabled", true),
        "getOne", Map.of("isEnabled", true),
        "search", Map.of("isEnabled", false),
        "update", Map.of("isEnabled", true),
        "delete", Map.of("isEnabled", true)));
    source.setExtraProp("custom", List.of(Map.of(
        "name", "approve",
        "title", "Approve order",
        "api", Map.of("isEnabled", true, "isGenerateImplementation", true, "actionInputMapping", Map.of())), Map.of(
        "name", "archive",
        "api", Map.of("isEnabled", false, "isGenerateImplementation", true, "actionInputMapping", Map.of()))));

    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());

    Map<String, ActionDefinition> actions = model.getBehavior().getActions();
    assertEquals("sales.LegacyOrder.getOne", actions.get("getOne").getBinding().getFlowName());
        assertEquals(true, actions.get("getOne").getReadOnly());
    assertEquals(false, actions.get("search").getIsEnabled());
    assertEquals(true, actions.get("search").getReadOnly());
        assertEquals("logicflow", actions.get("approve").getBinding().getKind());
    assertEquals("sales.LegacyOrder.approve", actions.get("approve").getBinding().getFlowName());
    assertFalse(actions.containsKey("archive"));
  }

  @Test
  void publicationValidatesTheRestrictedPreconditionAndRejectsUnsupportedAuthorization() {
    for (Object invalid : List.of("${true}", Map.of("expression", "${request == null}"),
        Map.of("expression", "${false and input.data.clear() == null}"), Map.of("expression", "${#external == null}"),
        Map.of("expression", "${objectStates == null}"), Map.of("expression", "${true}", "script", "anything"))) {
      var source = meta("sales", "Order");
      var action = action("submit", "sales.Order", null);
      action.setPrecondition(invalid);
      source.setActions(List.of(action));
      var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
      assertFalse(validation.isValid());
      assertTrue(validation.getErrors().toString().contains("actions.submit.precondition-invalid"));
    }
    var source = meta("sales", "Order");
    var action = action("submit", "sales.Order", null);
    action.setPrecondition(Map.of("expression", "${input.data.confirmed == true}", "message", "请确认"));
    source.setActions(List.of(action));
    assertTrue(new DefaultBusinessModel(source, emptyProvider()).getValidationResult().isValid());
    action.setAuthorization(Map.of("script", "true"));
    var validation = new DefaultBusinessModel(source, emptyProvider()).getValidationResult();
    assertTrue(validation.getErrors().toString().contains("actions.submit.authorization-unsupported-use-authority"));
  }

  @Test
  void defaultEventsArePublishedOnceAndPlansBelongOnlyToDefaultWrites() {
    var source = meta("sales", "Order");
    var id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("单行文本");
    id.setIsPrimaryKey(true);
    source.setFields(List.of(id));
    source.setExtraProp("apis", Map.of("add", Map.of("isEnabled", true),
        "search", Map.of("isEnabled", true, "readOnly", false),
        "update", Map.of("isEnabled", true, "readOnly", true)));
    source.setExtraProp("features", Map.of("isEnabledImport", true, "isEnabledBatchDelete", true));
    source.setExtraProp("custom", List.of(Map.of("name", "approve", "api", Map.of("isEnabled", false))));
    var model = new DefaultBusinessModel(source, emptyProvider());
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    var behavior = model.getBehavior();
    assertEquals(java.util.Set.of("recordsCreated", "recordsUpdated", "recordsDeleted"), behavior.getEvents().keySet());
    assertEquals(0, source.getEvents().size());
    for (String name : List.of("add", "delete", "batchDelete", "import")) {
      assertNotNull(behavior.mutationEventPlan(name));
      assertEquals(3, behavior.getActions().get(name).getEmits().size());
    }
    for (String name : List.of("update", "getOne", "search", "approve")) {
      assertNull(behavior.mutationEventPlan(name));
    }
    assertFalse(behavior.getActions().get("delete").getIsEnabled());
    assertTrue(behavior.getEvents().values().stream().allMatch(event -> event.isRequireUnfilteredModelRead()
        && event.getSubject().equals("model") && event.getAllowedProducerKinds().equals(List.of("action"))));
    source.setEvents(List.of(event("RECORDSCREATED")));
    var conflicted = new DefaultBusinessModel(source, emptyProvider());
    assertFalse(conflicted.getValidationResult().isValid());
    assertTrue(conflicted.getValidationResult().getErrors().toString().contains("default mutation projection"));
    assertTrue(new DefaultBusinessModel(meta("sales", "Dashboard"), emptyProvider()).getBehavior().getEvents().isEmpty());
  }

  @Test
  void shouldRouteCodeBoundCustomApiThroughActionRuntimeWithoutGeneratingAFlow() {
    DefaultBusinessModelMeta source = meta("sales", "CodeBoundOrder");
    source.setExtraProp("custom", List.of(Map.of(
        "name", "approve",
        "api", Map.of(
            "isEnabled", true,
            "isGenerateImplementation", false,
            "isBindAction", true,
            "actionInputMapping", Map.of("data", "$.body"),
            "httpMethod", "POST",
            "url", "approve"))));
    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    ModelProjectionPipelineContext<DefaultBusinessModel> context = new ModelProjectionPipelineContext<>(model);

    new CustomAPIMPS().process(context);

    WebAPIModel projected = context.getModelMap(WebAPIModel.class).values().stream()
        .filter(api -> "approve".equals(api.getExtraProp(String.class, "action").orElse(null)))
        .findFirst().orElseThrow();
    assertEquals(HandlerType.BUSINESS_ACTION, projected.getHandlerType());
    assertEquals("sales.CodeBoundOrder", projected.getExtraProp(String.class, "businessModel").orElseThrow());
    assertEquals("approve", projected.getExtraProp(String.class, "action").orElseThrow());
    assertTrue(projected.getExtraProp(String.class, "logicFlow").isEmpty());
  }

  @Test
  void shouldRejectAndOmitEnabledCustomApiWithoutAnActionBinding() {
    DefaultBusinessModelMeta source = meta("sales", "Order");
    source.setExtraProp("custom", List.of(Map.of(
        "name", "approve",
        "api", Map.of(
            "isEnabled", true,
            "isGenerateImplementation", false,
            "isBindAction", true,
            "actionInputMapping", Map.of("data", "$.body"),
            "httpMethod", "POST",
            "url", "approve"))));
    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());
    ModelProjectionPipelineContext<DefaultBusinessModel> context = new ModelProjectionPipelineContext<>(model);

    new CustomAPIMPS().process(context);

    assertFalse(model.getValidationResult().isValid());
    assertTrue(model.getValidationResult().getErrors().stream()
        .anyMatch(error -> error.getMessage().contains("actions.approve.binding-required")));
    assertTrue(context.getModelMap(WebAPIModel.class).values().stream()
        .noneMatch(api -> "approve".equals(api.getExtraProp(String.class, "action").orElse(null))));
  }

  @Test
  void shouldLeaveDirectHighCodeCustomApiOutsideActionRuntime() {
    DefaultBusinessModelMeta source = meta("sales", "DirectOrder");
    source.setExtraProp("custom", List.of(Map.of(
        "name", "approve",
        "api", Map.of(
            "isEnabled", true,
            "isGenerateImplementation", false,
            "httpMethod", "POST",
            "url", "approve"))));
    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    assertFalse(model.getBehavior().getActions().containsKey("approve"));

    ModelProjectionPipelineContext<DefaultBusinessModel> context = new ModelProjectionPipelineContext<>(model);
    new CustomAPIMPS().process(context);
    assertTrue(context.getModelMap(WebAPIModel.class).values().stream()
        .noneMatch(api -> "approve".equals(api.getExtraProp(String.class, "action").orElse(null))));
  }

  @Test
  void shouldIncludeRegisteredBehaviorContributionsInThePublishedSnapshot() {
    DefaultBusinessModel model = new DefaultBusinessModel(meta("sales", "Order"), emptyProvider());

    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    BusinessModelBehavior behavior = model.buildModelBundle()
        .getModel(BusinessModelBehavior.class, "sales.Order")
        .orElseThrow();

    assertTrue(behavior.getActions().containsKey("reserveStock"));
    assertTrue(behavior.getDefinitionConflicts().isEmpty());
  }

  @Test
  void publishedBehaviorSnapshotDoesNotShareNestedMutableSchemaValues() {
    List<String> required = new ArrayList<>(List.of("id"));
    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put("type", "object");
    inputSchema.put("required", required);
    ActionDefinition action = action("submit", "sales.Order", null);
    action.setInputSchema(Map.of("type", "object", "properties", Map.of("data", inputSchema)));
    DefaultBusinessModelMeta source = meta("sales", "Order");
    source.setActions(List.of(action));

    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());
    required.add("status");
    BusinessModelBehavior behavior = model.getBehavior();

    assertEquals(List.of("id"), ((Map<?, ?>) ((Map<?, ?>) behavior.getActions().get("submit").getInputSchema().get("properties")).get("data")).get("required"));
    ((List<?>) ((Map<?, ?>) ((Map<?, ?>) behavior.getActions().get("submit").getInputSchema().get("properties")).get("data")).get("required")).clear();
    assertEquals(List.of("id"), ((Map<?, ?>) ((Map<?, ?>) behavior.getActions().get("submit").getInputSchema().get("properties")).get("data")).get("required"));
  }

  @Test
  void shouldRejectMetadataAndSpiDefinitionsWithTheSameIdentityAndReportBothSources() {
    DefaultBusinessModelMeta source = meta("sales", "Order");
    source.setActions(List.of(action("reserveStock", "sales.Order", null)));
    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());

    ValidationResult result = model.getValidationResult();
    String errors = result.getErrors().toString();

    assertFalse(result.isValid());
    assertTrue(errors.contains("actions.reserveStock.duplicate"));
    assertTrue(errors.contains("metadata"));
    assertTrue(errors.contains("test contributor"));
  }

  @Test
  void shouldRejectInvalidSchemasAndUnresolvableCrossModelReferences() {
    DefaultBusinessModelMeta source = meta("sales", "Order");
    ActionDefinition action = action("submit", "sales.Order", "submitted");
    action.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of("type", "not-a-json-schema-type"))));
    source.setActions(List.of(action));
    source.setEvents(List.of(event("submitted")));
    source.setEventSubscriptions(List.of(subscription(
        "reserve", "inventory.Stock", "stockReserved", "inventory.Stock", "reserve")));

    DefaultBusinessModel model = new DefaultBusinessModel(source, emptyProvider());
    ValidationResult result = model.getValidationResult();
    String errors = result.getErrors().toString();

    assertFalse(result.isValid());
    assertTrue(errors.contains("actions.submit.inputSchema-invalid"));
    assertTrue(errors.contains("eventSubscriptions.reserve.source-event-model-not-found"));
    assertTrue(errors.contains("eventSubscriptions.reserve.target-action-model-not-found"));
  }

  @Test
  void persistentSubscriptionCannotOwnAFileResultFromEitherLocalOrReferencedAction() {
    for (String target : List.of("sales.Order", "inventory.Download")) {
      Map<String, BusinessModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      var provider = provider(models);
      var download = action("download", target, null);
      download.setReadOnly(true);
      download.setOutputSchema(Map.of("type", "string", "format", "binary"));
      var order = meta("sales", "Order");
      order.setEvents(List.of(event("submitted")));
      order.setEventSubscriptions(List.of(subscription("download", "sales.Order", "submitted", target, "download")));
      if ("sales.Order".equals(target)) {
        order.setActions(List.of(download));
      } else {
        var external = meta("inventory", "Download"); external.setActions(List.of(download));
        models.put(target, new DefaultBusinessModel(external, provider));
      }
      var model = new DefaultBusinessModel(order, provider); models.put("sales.Order", model);
      var result = model.getValidationResult();
      assertFalse(result.isValid());
      assertTrue(result.getErrors().toString().contains("eventSubscriptions.download.target-action-file-output-not-consumable"), result.getErrors().toString());
    }
  }

  @Test
  void shouldResolveSubscriptionMembersAcrossBusinessModels() {
    Map<String, BusinessModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    BusinessModelProvider provider = provider(models);
    DefaultBusinessModelMeta stockMeta = meta("inventory", "Stock");
    stockMeta.setActions(List.of(action("reserve", "inventory.Stock", null)));
    stockMeta.setEvents(List.of(event("reserved")));
    DefaultBusinessModel stockModel = new DefaultBusinessModel(stockMeta, provider);
    models.put(stockModel.getFullName(), stockModel);

    DefaultBusinessModelMeta orderMeta = meta("sales", "Order");
    orderMeta.setEventSubscriptions(List.of(subscription(
        "reserveStock", "inventory.Stock", "reserved", "inventory.Stock", "reserve")));
    DefaultBusinessModel orderModel = new DefaultBusinessModel(orderMeta, provider);
    models.put(orderModel.getFullName(), orderModel);

    ValidationResult result = orderModel.getValidationResult();

    assertTrue(result.isValid(), result.getErrors().toString());
  }

  private static DefaultBusinessModelMeta meta(String namespace, String name) {
    DefaultBusinessModelMeta meta = new DefaultBusinessModelMeta();
    meta.setNamespace(namespace);
    meta.setName(name);
    meta.setTitle(name);
    meta.setIsEnabled(true);
    return meta;
  }

  private static ActionDefinition action(String name, String modelFullName, String emittedEvent) {
    ActionDefinition action = new ActionDefinition();
    action.setName(name);
    action.setTitle(name);

    action.setIsEnabled(true);
    ActionBinding binding = new ActionBinding();
    binding.setKind("handler");
    binding.setHandlerName("handler." + name);
    action.setBinding(binding);
    action.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of("type", "object"))));
    action.setOutputSchema(Map.of("type", "object"));
    if (emittedEvent != null) {
      EventReference event = new EventReference();
      event.setModelFullName(modelFullName);
      event.setEventName(emittedEvent);
      action.setEmits(List.of(event));
    }
    return action;
  }

  private static ActionDefinition actionWithObjectReference(String name, String referencedModelFullName) {
    ActionDefinition action = action(name, "sales.Order", null);
    action.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of(
        "type", "object",
        "properties", Map.of("customer", Map.of(
            "type", "array",
            "items", Map.of(
                "type", "object",
                "x-business-object-ref", referencedModelFullName,
                "properties", Map.of(
                    "modelFullName", Map.of("type", "string"),
                    "id", Map.of("type", "string")),
                "required", List.of("modelFullName", "id")))),
        "required", List.of("customer"),
        "additionalProperties", false))));
    return action;
  }

  private static EventDefinition event(String name) {
    EventDefinition event = new EventDefinition();
    event.setName(name);
    event.setTitle(name);
    event.setOccurrenceCondition("业务事实 " + name);
    event.setSubject("model");
    event.setPayloadSchema(Map.of("type", "object"));
    event.setAllowedProducerKinds(List.of("action"));
    return event;
  }

  private static com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition subscription(
      String name, String sourceModelFullName, String sourceEventName,
      String targetModelFullName, String targetActionName) {
    EventSubscriptionDefinition subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
    subscription.setName(name);
    subscription.getSourceEvent().setModelFullName(sourceModelFullName);
    subscription.getSourceEvent().setEventName(sourceEventName);
    subscription.getTarget().setModelFullName(targetModelFullName);
    subscription.getTarget().setKind("action"); subscription.getTarget().setActionName(targetActionName);
    return subscription;
  }

  private static BusinessModelProvider emptyProvider() {
    return provider(Collections.emptyMap());
  }

  private static BusinessModelProvider provider(Map<String, BusinessModel> models) {
    return new BusinessModelProvider() {
      @Override
      public Optional<BusinessModel> getBusinessModel(String fullName) {
        return Optional.ofNullable(models.get(fullName));
      }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() {
        return models;
      }
    };
  }
}
