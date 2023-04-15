/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.util.Collection;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.BasicCollectionJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcLiteralFormatter;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * A converted basic array type.
 *
 * @author Christian Beikov
 */
public class ConvertedBasicCollectionType<C extends Collection<E>, E> extends BasicCollectionType<C, E> {

	private final BasicValueConverter<C, ?> converter;
	private final ValueExtractor<C> jdbcValueExtractor;
	private final ValueBinder<C> jdbcValueBinder;
	private final JdbcLiteralFormatter<C> jdbcLiteralFormatter;

	@SuppressWarnings("unchecked")
	public ConvertedBasicCollectionType(
			BasicType<E> baseDescriptor,
			JdbcType arrayJdbcType,
			BasicCollectionJavaType<C, E> arrayTypeDescriptor,
			BasicValueConverter<C, ?> converter) {
		super( baseDescriptor, arrayJdbcType, arrayTypeDescriptor );
		this.converter = converter;
		this.jdbcValueBinder = (ValueBinder<C>) arrayJdbcType.getBinder( converter.getRelationalJavaType() );
		this.jdbcValueExtractor = (ValueExtractor<C>) arrayJdbcType.getExtractor( converter.getRelationalJavaType() );
		this.jdbcLiteralFormatter = (JdbcLiteralFormatter<C>) arrayJdbcType.getJdbcLiteralFormatter( converter.getRelationalJavaType() );
	}

	@Override
	public BasicValueConverter<C, ?> getValueConverter() {
		return converter;
	}

	@Override
	public JavaType<?> getJdbcJavaType() {
		return converter.getRelationalJavaType();
	}

	@Override
	public ValueExtractor<C> getJdbcValueExtractor() {
		return jdbcValueExtractor;
	}

	@Override
	public ValueBinder<C> getJdbcValueBinder() {
		return jdbcValueBinder;
	}

	@Override
	public JdbcLiteralFormatter<C> getJdbcLiteralFormatter() {
		return jdbcLiteralFormatter;
	}
}
