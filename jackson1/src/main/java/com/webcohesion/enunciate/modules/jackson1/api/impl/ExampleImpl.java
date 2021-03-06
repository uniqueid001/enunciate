package com.webcohesion.enunciate.modules.jackson1.api.impl;

import com.webcohesion.enunciate.EnunciateException;
import com.webcohesion.enunciate.api.datatype.Example;
import com.webcohesion.enunciate.facets.FacetFilter;
import com.webcohesion.enunciate.javac.decorations.element.ElementUtils;
import com.webcohesion.enunciate.javac.javadoc.JavaDoc;
import com.webcohesion.enunciate.metadata.DocumentationExample;
import com.webcohesion.enunciate.modules.jackson1.model.*;
import com.webcohesion.enunciate.modules.jackson1.model.types.JsonArrayType;
import com.webcohesion.enunciate.modules.jackson1.model.types.JsonClassType;
import com.webcohesion.enunciate.modules.jackson1.model.types.JsonMapType;
import com.webcohesion.enunciate.modules.jackson1.model.types.JsonType;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

import java.io.IOException;
import java.util.LinkedList;

/**
 * @author Ryan Heaton
 */
public class ExampleImpl implements Example {

  private final ObjectTypeDefinition type;

  public ExampleImpl(ObjectTypeDefinition type) {
    this.type = type;
  }

  @Override
  public String getLang() {
    return "js";
  }

  @Override
  public String getBody() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();

    Context context = new Context();
    context.stack = new LinkedList<String>();
    build(node, this.type, context);

    ObjectMapper mapper = new ObjectMapper().enable(SerializationConfig.Feature.INDENT_OUTPUT);
    try {
      return mapper.writeValueAsString(node);
    }
    catch (JsonProcessingException e) {
      throw new EnunciateException(e);
    }
    catch (IOException e) {
      throw new EnunciateException(e);
    }
  }

  private void build(ObjectNode node, ObjectTypeDefinition type, Context context) {
    if (context.stack.size() > 2) {
      //don't go deeper than 2 for fear of the OOM (see https://github.com/stoicflame/enunciate/issues/139).
      return;
    }

    FacetFilter facetFilter = type.getContext().getContext().getConfiguration().getFacetFilter();
    for (Member member : type.getMembers()) {
      if (!facetFilter.accept(member)) {
        continue;
      }

      if (ElementUtils.findDeprecationMessage(member) != null) {
        continue;
      }

      String example = null;

      JavaDoc.JavaDocTagList tags = member.getJavaDoc().get("documentationExample");
      if (tags != null && tags.size() > 0) {
        String tag = tags.get(0).trim();
        example = tag.isEmpty() ? null : tag;
      }

      DocumentationExample documentationExample = member.getAnnotation(DocumentationExample.class);
      if (documentationExample != null) {
        if (documentationExample.exclude()) {
          continue;
        }
        else if (context.currentIndex == 1 && !"##default".equals(documentationExample.value2())) {
          example = documentationExample.value2();
        }
        else if (!"##default".equals(documentationExample.value())) {
          example = documentationExample.value();
        }
      }

      if (member.getChoices().size() > 1) {
        if (member.isCollectionType()) {
          final ArrayNode exampleNode = JsonNodeFactory.instance.arrayNode();

          for (Member choice : member.getChoices()) {
            JsonType jsonType = choice.getJsonType();
            String choiceName = choice.getName();
            if ("".equals(choiceName)) {
              choiceName = "...";
            }

            if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_ARRAY) {
              ArrayNode wrapperNode = JsonNodeFactory.instance.arrayNode();
              wrapperNode.add(choiceName);
              wrapperNode.add(exampleNode(jsonType, example, context));
              exampleNode.add(wrapperNode);
            }
            else if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_OBJECT) {
              ObjectNode wrapperNode = JsonNodeFactory.instance.objectNode();
              wrapperNode.put(choiceName, exampleNode(jsonType, example, context));
              exampleNode.add(wrapperNode);
            }
            else {
              exampleNode.add(exampleNode(jsonType, example, context));
            }
          }

          node.put(member.getName(), exampleNode);
        }
        else {
          for (Member choice : member.getChoices()) {
            JsonNode exampleNode;
            JsonType jsonType = choice.getJsonType();
            String choiceName = choice.getName();
            if ("".equals(choiceName)) {
              choiceName = "...";
            }

            if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_ARRAY) {
              ArrayNode wrapperNode = JsonNodeFactory.instance.arrayNode();
              wrapperNode.add(choiceName);
              wrapperNode.add(exampleNode(jsonType, example, context));
              exampleNode = wrapperNode;
            }
            else if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_OBJECT) {
              ObjectNode wrapperNode = JsonNodeFactory.instance.objectNode();
              wrapperNode.put(choiceName, exampleNode(jsonType, example, context));
              exampleNode = wrapperNode;
            }
            else {
              exampleNode = exampleNode(jsonType, example, context);
            }

            node.put(member.getName(), exampleNode);
          }
        }
      }
      else {
        node.put(member.getName(), exampleNode(member.getJsonType(), example, context));
      }
    }

    JsonType supertype = type.getSupertype();
    if (supertype instanceof JsonClassType && ((JsonClassType)supertype).getTypeDefinition() instanceof ObjectTypeDefinition) {
      build(node, (ObjectTypeDefinition) ((JsonClassType) supertype).getTypeDefinition(), context);
    }

    if (type.getWildcardMember() != null && ElementUtils.findDeprecationMessage(type.getWildcardMember()) == null) {
      node.put("extension1", "...");
      node.put("extension2", "...");
    }

  }

  private JsonNode exampleNode(JsonType jsonType, String specifiedExample, Context context) {
    if (jsonType instanceof JsonClassType) {
      TypeDefinition typeDefinition = ((JsonClassType) jsonType).getTypeDefinition();
      if (typeDefinition instanceof ObjectTypeDefinition) {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        if (!context.stack.contains(typeDefinition.getQualifiedName().toString())) {
          context.stack.push(typeDefinition.getQualifiedName().toString());
          try {
            build(objectNode, (ObjectTypeDefinition) typeDefinition, context);
          }
          finally {
            context.stack.pop();
          }
        }
        return objectNode;
      }
      else if (typeDefinition instanceof EnumTypeDefinition) {
        String example = "???";

        if (specifiedExample != null) {
          example = specifiedExample;
        }
        else if (((EnumTypeDefinition) typeDefinition).getEnumValues().size() > 0) {
          example = ((EnumTypeDefinition) typeDefinition).getEnumValues().iterator().next().getValue();
        }

        return JsonNodeFactory.instance.textNode(example);
      }
      else {
        return exampleNode(((SimpleTypeDefinition) typeDefinition).getBaseType(), specifiedExample, context);
      }
    }
    else if (jsonType instanceof JsonMapType) {
      ObjectNode mapNode = JsonNodeFactory.instance.objectNode();
      mapNode.put("property1", "...");
      mapNode.put("property2", "...");
      return mapNode;
    }
    else if (jsonType.isArray()) {
      ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
      JsonNode componentNode = exampleNode(((JsonArrayType) jsonType).getComponentType(), specifiedExample, context);
      arrayNode.add(componentNode);
      Context context2 = new Context();
      context2.stack = context.stack;
      context2.currentIndex = 1;
      JsonNode componentNode2 = exampleNode(((JsonArrayType) jsonType).getComponentType(), specifiedExample, context2);
      arrayNode.add(componentNode2);
      return arrayNode;
    }
    else if (jsonType.isWholeNumber()) {
      Long example = 12345L;
      if (specifiedExample != null) {
        try {
          example = Long.parseLong(specifiedExample);
        }
        catch (NumberFormatException e) {
          this.type.getContext().getContext().getLogger().warn("\"%s\" was provided as a documentation example, but it is not a valid JSON whole number, so it will be ignored.", specifiedExample);
        }
      }
      return JsonNodeFactory.instance.numberNode(example);
    }
    else if (jsonType.isNumber()) {
      Double example = 12345D;
      if (specifiedExample != null) {
        try {
          example = Double.parseDouble(specifiedExample);
        }
        catch (NumberFormatException e) {
          this.type.getContext().getContext().getLogger().warn("\"%s\" was provided as a documentation example, but it is not a valid JSON number, so it will be ignored.", specifiedExample);
        }
      }
      return JsonNodeFactory.instance.numberNode(example);
    }
    else if (jsonType.isBoolean()) {
      boolean example = !"false".equals(specifiedExample);
      return JsonNodeFactory.instance.booleanNode(example);
    }
    else if (jsonType.isString()) {
      String example = specifiedExample;
      if (example == null) {
        example = "...";
      }
      return JsonNodeFactory.instance.textNode(example);
    }
    else {
      return JsonNodeFactory.instance.objectNode();
    }
  }

  private static class Context {
    LinkedList<String> stack;
    int currentIndex = 0;
  }
}
