/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm

import org.springframework.datastore.mapping.core.Datastore
import org.springframework.datastore.mapping.model.MappingContext
import static org.springframework.datastore.mapping.validation.ValidatingEventListener.ERRORS_ATTRIBUTE
import org.springframework.validation.*

 /**
 * Methods used for validating GORM instances
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 * @since 1.0
 */
class GormValidationApi<D> extends AbstractGormApi<D> {

    Validator validator

    GormValidationApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
        MappingContext context = datastore.mappingContext
        def entity = context.getPersistentEntity(persistentClass.name)
        validator = context.getEntityValidator(entity)
    }

    /**
     * Validates an instance for the given arguments
     *
     * @param instance The instance to validate
     * @param arguments The arguments to use
     * @return True if the instance is valid
     */
    boolean validate(D instance, Map arguments) {
        validate instance, Collections.emptyList()
    }

    /**
     * Validates an instance
     *
     * @param instance The instance to validate
     * @param fields The list of fields to validate
     * @return True if the instance is valid
     */
    boolean validate(D instance, List fields) {
        if (!validator) {
            return true
        }

        def localErrors = new BeanPropertyBindingResult(instance, instance.getClass().name)

        Errors errors = instance.errors
        validator.validate instance, localErrors

        if (fields) {
            localErrors = filterErrors(localErrors, fields as Set, instance)
        }

        if (localErrors.hasErrors()) {
            Errors objectErrors = errors
            localErrors.allErrors.each { localError ->
                if (localError instanceof FieldError) {
                    def fieldName = localError.getField()
                    def fieldError = objectErrors.getFieldError(fieldName)

                    // if we didn't find an error OR if it is a bindingFailure...
                    if (!fieldError || fieldError.bindingFailure) {
                        objectErrors.addError(localError)
                    }
                }
            }
            instance.errors = objectErrors
        }
        return !errors.hasErrors()
    }

    private Errors filterErrors(Errors errors, Set validatedFields, Object target) {
        if (validatedFields == null || validatedFields.isEmpty()) return errors

        BeanPropertyBindingResult result = new BeanPropertyBindingResult(
            target, target.getClass().getName())

        for (ObjectError error : errors.getAllErrors()) {

            if (error instanceof FieldError) {
                if (!validatedFields.contains(error.getField())) continue
            }

            result.addError(error)
        }

        return result
    }

    /**
     * Validates an instance
     *
     * @param instance The instance to validate
     * @return True if the instance is valid
     */
    boolean validate(D instance) {
        validate instance, Collections.emptyList()
    }

    /**
     * Validates an instance. Note: This signature is purely here for compatibility the
     * evict parameter does nothing and the method should be regarded as deprecated
     *
     * @param instance The instance to validate
     * @return True if the instance is valid
     */
    @Deprecated
    boolean validate(D instance, boolean evict) {
        validate instance, Collections.emptyList()
    }

    /**
     * Obtains the errors for an instance
     * @param instance The instance to obtain errors for
     * @return The {@link Errors} instance
     */
    Errors getErrors(D instance) {
        def session = datastore.currentSession
        def errors = session.getAttribute(instance, ERRORS_ATTRIBUTE)
        if (errors == null) {
            errors = resetErrors(instance)
        }
        return errors
    }

    private Errors resetErrors(D instance) {
        def er = new BeanPropertyBindingResult(instance, persistentClass.name)
        instance.errors = er
        return er
    }

    /**
     * Sets the errors for an instance
     * @param instance The instance
     * @param errors The errors
     */
    void setErrors(D instance, Errors errors) {
        datastore.currentSession.setAttribute(instance, ERRORS_ATTRIBUTE, errors)
    }

    /**
     * Clears any errors that exist on an instance
     * @param instance The instance
     */
    void clearErrors(D instance) {
        resetErrors(instance)
    }

    /**
     * Tests whether an instance has any errors
     * @param instance The instance
     * @return True if errors exist
     */
    boolean hasErrors(D instance) {
        instance.errors?.hasErrors()
    }
}
