/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.Incubating;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Specifies the UDT (user defined type) name for the annotated embeddable or embedded.
 *
 * <pre>
 *    Example:
 *
 *    {@code @Embeddable}
 *    {@code Struct(name = "CUST")}
 *    public class Customer { ... }
 * </pre>
 *
 * <pre>
 *    Example:
 *
 *    public class Order {
 *        {@code Embedded}
 *        {@code Struct(name = "CUST")}
 *        private Customer customer;
 *    }
 * </pre>
 *
 * @since 6.2
 */
@Incubating
@Target({TYPE, FIELD, METHOD})
@Retention( RetentionPolicy.RUNTIME )
public @interface Struct {
	/**
	 * The name of the UDT (user defined type).
	 */
	String name();

	/**
	 * The ordered set of attributes of the UDT, as they appear physically in the DDL.
	 * It is important to specify the attributes in the same order for JDBC interactions to work correctly.
	 * If the annotated type is a record, the order of record components is used as the default order.
	 * If no default order can be inferred, attributes are assumed to be in alphabetical order.
	 */
	String[] attributes() default {};
}
