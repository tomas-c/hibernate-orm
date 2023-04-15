/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.descriptor.java.spi;

import java.lang.reflect.Type;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractJavaType;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

/**
 * {@link AbstractJavaType} adapter for cases where we do not know a proper
 * {@link org.hibernate.type.descriptor.java.JavaType} for a given Java type.
 */
public final class UnknownBasicJavaType<T> extends AbstractJavaType<T> {
	public UnknownBasicJavaType(Class<T> type) {
		super( type );
	}

	public UnknownBasicJavaType(Class<T> type, MutabilityPlan<T> mutabilityPlan) {
		super( type, mutabilityPlan );
	}

	public UnknownBasicJavaType(Type type, MutabilityPlan<T> mutabilityPlan) {
		super( type, mutabilityPlan );
	}

	@Override
	public JdbcType getRecommendedJdbcType(JdbcTypeIndicators context) {
		throw new JdbcTypeRecommendationException(
				"Could not determine recommended JdbcType for `" + getJavaType().getTypeName() + "`"
		);
	}

	@Override
	public String toString(T value) {
		return value.toString();
	}

	@Override
	public T fromString(CharSequence string) {
		throw new UnsupportedOperationException(
				"Conversion from String strategy not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public <X> X unwrap(T value, Class<X> type, WrapperOptions options) {
		if ( type.isAssignableFrom( getJavaTypeClass() ) ) {
			//noinspection unchecked
			return (X) value;
		}
		throw new UnsupportedOperationException(
				"Unwrap strategy not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public <X> T wrap(X value, WrapperOptions options) {
		if ( getJavaTypeClass().isInstance( value ) ) {
			//noinspection unchecked
			return (T) value;
		}
		throw new UnsupportedOperationException(
				"Wrap strategy not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public String toString() {
		return "BasicJavaType(" + getJavaType().getTypeName() + ")";
	}
}
