/*
 * Copyright 2006 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.enunciate.modules.amf;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.TypeMirror;
import net.sf.jelly.apt.decorations.TypeMirrorDecorator;
import net.sf.jelly.apt.decorations.type.DecoratedDeclaredType;
import org.codehaus.enunciate.contract.jaxb.*;
import org.codehaus.enunciate.contract.jaxb.adapters.Adaptable;
import org.codehaus.enunciate.contract.jaxws.EndpointImplementation;
import org.codehaus.enunciate.contract.jaxws.EndpointInterface;
import org.codehaus.enunciate.contract.jaxws.WebMethod;
import org.codehaus.enunciate.contract.jaxws.WebParam;
import org.codehaus.enunciate.contract.validation.BaseValidator;
import org.codehaus.enunciate.contract.validation.ValidationResult;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The validator for the xfire-client module.
 *
 * @author Ryan Heaton
 */
public class AMFValidator extends BaseValidator {

  private final Set<String> unsupportedTypes = new HashSet<String>();

  public AMFValidator() {
    unsupportedTypes.add(QName.class.getName());
    unsupportedTypes.add(XMLGregorianCalendar.class.getName());
    unsupportedTypes.add(javax.xml.datatype.Duration.class.getName());
    unsupportedTypes.add(java.awt.Image.class.getName());
    unsupportedTypes.add(javax.xml.transform.Source.class.getName());
  }

  @Override
  public ValidationResult validateEndpointInterface(EndpointInterface ei) {
    ValidationResult result = super.validateEndpointInterface(ei);

    if (!isAMFTransient(ei)) {
      for (WebMethod webMethod : ei.getWebMethods()) {
        if (!isAMFTransient(webMethod)) {
          if (!isSupported(webMethod.getWebResult())) {
            result.addError(webMethod.getPosition(), "AMF doesn't support '" + webMethod.getWebResult() + "' as a return type.");
          }
          for (WebParam webParam : webMethod.getWebParameters()) {
            if (!isSupported(webParam.getType())) {
              result.addError(webParam.getPosition(), "AMF doesn't support '" + webParam.getType() + "' as a parameter type.");
            }
          }
        }
      }

      if (ei.getEndpointImplementations().size() > 1) {
        ArrayList<String> impls = new ArrayList<String>();
        for (EndpointImplementation impl : ei.getEndpointImplementations()) {
          impls.add(impl.getQualifiedName());
        }
        result.addError(ei.getPosition(), "Sorry, AMF doesn't support two endpoint implementations for interface '" + ei.getQualifiedName() +
          "'.  Found " + ei.getEndpointImplementations().size() + " implementations (" + impls.toString() + ").");
      }
    }
    
    return result;
  }

  @Override
  public ValidationResult validateComplexType(ComplexTypeDefinition complexType) {
    ValidationResult result = super.validateComplexType(complexType);
    if (!isAMFTransient(complexType)) {
      if (!hasDefaultConstructor(complexType)) {
        result.addError(complexType.getPosition(), "The mapping from AMF to JAXB requires a public no-arg constructor.");
      }

      for (Attribute attribute : complexType.getAttributes()) {
        if (!isAMFTransient(attribute)) {
          if (attribute.getDelegate() instanceof FieldDeclaration) {
            result.addError(attribute.getPosition(), "If you're mapping to AMF, you can't use fields for your accessors. ");
          }

          if (!isSupported(attribute.getAccessorType())) {
            result.addError(attribute.getPosition(), "AMF doesn't support the '" + attribute.getAccessorType() + "' type.");
          }
        }
      }

      for (Element element : complexType.getElements()) {
        if (!isAMFTransient(element)) {
          if (element.getDelegate() instanceof FieldDeclaration) {
            result.addError(element.getPosition(), "If you're mapping to AMF, you can't use fields for your accessors. ");
          }

          if (!isSupported(element.getAccessorType())) {
            result.addError(element.getPosition(), "AMF doesn't support the '" + element.getAccessorType() + "' type.");
          }
        }
      }

      Value value = complexType.getValue();
      if (value != null) {
        if (!isAMFTransient(value)) {
          if (value.getDelegate() instanceof FieldDeclaration) {
            result.addError(value.getPosition(), "If you're mapping to AMF, you can't use fields for your accessors. ");
          }

          if (!isSupported(value.getAccessorType())) {
            result.addError(value.getPosition(), "AMF doesn't support the '" + value.getAccessorType() + "' type.");
          }
        }
      }
    }

    return result;
  }


  @Override
  public ValidationResult validateSimpleType(SimpleTypeDefinition simpleType) {
    ValidationResult result = super.validateSimpleType(simpleType);
    if (!isAMFTransient(simpleType)) {
      if (!hasDefaultConstructor(simpleType)) {
        result.addError(simpleType.getPosition(), "The mapping from AMF to JAXB requires a public no-arg constructor.");
      }
    }
    return result;
  }


  private boolean hasDefaultConstructor(TypeDefinition typeDefinition) {
    Collection<ConstructorDeclaration> constructors = typeDefinition.getConstructors();
    for (ConstructorDeclaration constructor : constructors) {
      if ((constructor.getModifiers().contains(Modifier.PUBLIC)) && (constructor.getParameters().isEmpty())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the given type is supported.
   *
   * @param type The type to test for supportability.
   * @return Whether the given type is supported.
   */
  protected boolean isSupported(TypeMirror type) {
    if ((type instanceof Adaptable) && ((Adaptable) type).isAdapted()) {
      return isSupported(((Adaptable) type).getAdapterType().getAdaptingType());
    }
    else if (type instanceof DeclaredType) {
      DecoratedDeclaredType declaredType = (DecoratedDeclaredType) TypeMirrorDecorator.decorate(type);
      if ((declaredType.getDeclaration() != null) && (isAMFTransient(declaredType.getDeclaration()))) {
        return false;
      }
      else if ((declaredType.isInstanceOf(Collection.class.getName())) || (declaredType.isInstanceOf(java.util.Map.class.getName()))) {
        boolean supported = true;
        for (TypeMirror typeArgument : declaredType.getActualTypeArguments()) {
          supported &= isSupported(typeArgument);
        }
        return supported;
      }
      else {
        return !unsupportedTypes.contains(declaredType.getDeclaration().getQualifiedName());
      }
    }

    //by default, we're going to assume that the type is complex and is supported.
    return true;
  }

  /**
   * Whether the given type declaration is AMF-transient.
   *
   * @param declaration The type declaration.
   * @return Whether the given tyep declaration is AMF-transient.
   */
  protected boolean isAMFTransient(TypeDeclaration declaration) {
    return isAMFTransient((Declaration) declaration) || isAMFTransient(declaration.getPackage());
  }

  /**
   * Whether the given type declaration is AMF-transient.
   *
   * @param declaration The type declaration.
   * @return Whether the given tyep declaration is AMF-transient.
   */
  protected boolean isAMFTransient(Declaration declaration) {
    return declaration != null && declaration.getAnnotation(AMFTransient.class) != null;
  }

}