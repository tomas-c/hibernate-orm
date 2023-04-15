/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.util.Currency;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * Descriptor for {@link Currency} handling.
 *
 * @author Steve Ebersole
 */
public class CurrencyJavaType extends AbstractClassJavaType<Currency> {
	public static final CurrencyJavaType INSTANCE = new CurrencyJavaType();

	public CurrencyJavaType() {
		super( Currency.class );
	}

	@Override
	public String toString(Currency value) {
		return value.getCurrencyCode();
	}

	@Override
	public Currency fromString(CharSequence string) {
		return Currency.getInstance( string.toString() );
	}

	@SuppressWarnings("unchecked")
	public <X> X unwrap(Currency value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( String.class.isAssignableFrom( type ) ) {
			return (X) value.getCurrencyCode();
		}
		throw unknownUnwrap( type );
	}

	@Override
	public <X> Currency wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if (value instanceof String) {
			return Currency.getInstance( (String) value );
		}
		throw unknownWrap( value.getClass() );
	}

	@Override
	public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
		return 3;
	}

	@Override
	public boolean isWider(JavaType<?> javaType) {
		// This is necessary to allow comparing/assigning a currency attribute against a literal of the JdbcType
		switch ( javaType.getJavaType().getTypeName() ) {
			case "java.lang.String":
				return true;
			default:
				return false;
		}
	}
}
