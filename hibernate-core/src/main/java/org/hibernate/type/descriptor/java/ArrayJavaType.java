/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.util.function.Function;

import org.hibernate.HibernateException;
import org.hibernate.SharedSessionContract;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.BinaryStream;
import org.hibernate.engine.jdbc.internal.BinaryStreamImpl;
import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.descriptor.converter.internal.ArrayConverter;
import org.hibernate.type.BasicArrayType;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.BasicType;
import org.hibernate.type.ConvertedBasicArrayType;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Descriptor for {@code T[]} handling.
 *
 * @author Christian Beikov
 * @author Jordan Gigov
 */
public class ArrayJavaType<T> extends AbstractArrayJavaType<T[], T> {

	public ArrayJavaType(BasicType<T> baseDescriptor) {
		this( baseDescriptor.getJavaTypeDescriptor() );
	}

	public ArrayJavaType(JavaType<T> baseDescriptor) {
		super(
				(Class<T[]>) Array.newInstance( baseDescriptor.getJavaTypeClass(), 0 ).getClass(),
				baseDescriptor,
				new ArrayMutabilityPlan<>( baseDescriptor )
		);
	}

	@Override
	public BasicType<?> resolveType(
			TypeConfiguration typeConfiguration,
			Dialect dialect,
			BasicType<T> elementType,
			ColumnTypeInformation columnTypeInformation,
			JdbcTypeIndicators stdIndicators) {
		if ( stdIndicators.isLob() ) {
			final Class<?> javaTypeClass = getJavaTypeClass();
			if ( javaTypeClass == Byte[].class ) {
				return typeConfiguration.getBasicTypeRegistry().resolve(
						ByteArrayJavaType.INSTANCE,
						ByteArrayJavaType.INSTANCE.getRecommendedJdbcType( stdIndicators )
				);
			}
			if ( javaTypeClass == Character[].class ) {
				return typeConfiguration.getBasicTypeRegistry().resolve(
						CharacterArrayJavaType.INSTANCE,
						CharacterArrayJavaType.INSTANCE.getRecommendedJdbcType( stdIndicators )
				);
			}
		}
		final Class<?> elementJavaTypeClass = elementType.getJavaTypeDescriptor().getJavaTypeClass();
		if ( elementType instanceof BasicPluralType<?, ?> || elementJavaTypeClass != null && elementJavaTypeClass.isArray() ) {
			return null;
		}
		final ArrayJavaType<T> arrayJavaType;
		if ( getElementJavaType() == elementType.getJavaTypeDescriptor() ) {
			arrayJavaType = this;
		}
		else {
			arrayJavaType = new ArrayJavaType<>( elementType.getJavaTypeDescriptor() );
			// Register the array type as that will be resolved in the next step
			typeConfiguration.getJavaTypeRegistry().addDescriptor( arrayJavaType );
		}
		//noinspection unchecked
		final BasicValueConverter<Object, Object> valueConverter = (BasicValueConverter<Object, Object>) elementType.getValueConverter();
		if ( valueConverter == null ) {
			final Function<JavaType<T[]>, BasicType<T[]>> creator = javaType -> {
				JdbcType arrayJdbcType = typeConfiguration.getJdbcTypeRegistry().getDescriptor( Types.ARRAY );
				if ( arrayJdbcType instanceof ArrayJdbcType ) {
					arrayJdbcType = ( (ArrayJdbcType) arrayJdbcType ).resolveType(
							typeConfiguration,
							dialect,
							elementType,
							columnTypeInformation
					);
				}
				return new BasicArrayType<>( elementType, arrayJdbcType, javaType );
			};
			if ( typeConfiguration.getBasicTypeRegistry().getRegisteredType( elementType.getName() ) == elementType ) {
				return typeConfiguration.standardBasicTypeForJavaType( arrayJavaType.getJavaType(), creator );
			}
			return creator.apply( arrayJavaType );
		}
		else {
			final JavaType<Object> relationalJavaType = typeConfiguration.getJavaTypeRegistry().getDescriptor(
					Array.newInstance( valueConverter.getRelationalJavaType().getJavaTypeClass(), 0 ).getClass()
			);

			JdbcType arrayJdbcType = typeConfiguration.getJdbcTypeRegistry().getDescriptor( Types.ARRAY );
			if ( arrayJdbcType instanceof ArrayJdbcType ) {
				arrayJdbcType = ( (ArrayJdbcType) arrayJdbcType ).resolveType(
						typeConfiguration,
						dialect,
						elementType,
						columnTypeInformation
				);
			}
			//noinspection unchecked
			return new ConvertedBasicArrayType<>(
					elementType,
					arrayJdbcType,
					arrayJavaType,
					new ArrayConverter<>( valueConverter, arrayJavaType, relationalJavaType )
			);
		}
	}

	@Override
	public String extractLoggableRepresentation(T[] value) {
		if ( value == null ) {
			return "null";
		}
		int iMax = value.length - 1;
		if ( iMax == -1 ) {
			return "[]";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append( '[' );
		for ( int i = 0; ; i++ ) {
			sb.append( getElementJavaType().extractLoggableRepresentation( value[i] ) );
			if ( i == iMax ) {
				return sb.append( ']' ).toString();
			}
			sb.append( ", " );
		}
	}

	@Override
	public boolean areEqual(T[] one, T[] another) {
		if ( one == null && another == null ) {
			return true;
		}
		if ( one == null || another == null ) {
			return false;
		}
		if ( one.length != another.length ) {
			return false;
		}
		int l = one.length;
		for ( int i = 0; i < l; i++ ) {
			if ( !getElementJavaType().areEqual( one[i], another[i] )) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int extractHashCode(T[] value) {
		if ( value == null ) {
			return 0;
		}

		int result = 1;

		for ( T element : value ) {
			result = 31 * result + ( element == null ? 0 : getElementJavaType().extractHashCode( element ) );
		}
		return result;
	}

	@Override
	public String toString(T[] value) {
		if ( value == null ) {
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		sb.append( '{' );
		String glue = "";
		for ( T v : value ) {
			sb.append( glue );
			if ( v == null ) {
				sb.append( "null" );
				glue = ",";
				continue;
			}
			sb.append( '"' );
			String valstr = getElementJavaType().toString( v );
			// using replaceAll is a shorter, but much slower way to do this
			for (int i = 0, len = valstr.length(); i < len; i ++ ) {
				char c = valstr.charAt( i );
				// Surrogate pairs. This is how they're done.
				if (c == '\\' || c == '"') {
					sb.append( '\\' );
				}
				sb.append( c );
			}
			sb.append( '"' );
			glue = ",";
		}
		sb.append( '}' );
		final String result = sb.toString();
		return result;
	}

	@Override
	public T[] fromString(CharSequence charSequence) {
		if ( charSequence == null ) {
			return null;
		}
		java.util.ArrayList<String> lst = new java.util.ArrayList<>();
		StringBuilder sb = null;
		char lastChar = charSequence.charAt( charSequence.length() - 1 );
		char firstChar = charSequence.charAt( 0 );
		if ( firstChar != '{' || lastChar != '}' ) {
			throw new IllegalArgumentException( "Cannot parse given string into array of strings. First and last character must be { and }" );
		}
		int len = charSequence.length();
		boolean inquote = false;
		for ( int i = 1; i < len; i ++ ) {
			char c = charSequence.charAt( i );
			if ( c == '"' ) {
				if (inquote) {
					lst.add( sb.toString() );
				}
				else {
					sb = new StringBuilder();
				}
				inquote = !inquote;
				continue;
			}
			else if ( !inquote ) {
				if ( Character.isWhitespace( c ) ) {
					continue;
				}
				else if ( c == ',' ) {
					// treat no-value between commas to mean null
					if ( sb == null ) {
						lst.add( null );
					}
					else {
						sb = null;
					}
					continue;
				}
				else {
					// i + 4, because there has to be a comma or closing brace after null
					if ( i + 4 < len
							&& charSequence.charAt( i ) == 'n'
							&& charSequence.charAt( i + 1 ) == 'u'
							&& charSequence.charAt( i + 2 ) == 'l'
							&& charSequence.charAt( i + 3 ) == 'l') {
						lst.add( null );
						i += 4;
						continue;
					}
					if (i + 1 == len) {
						break;
					}
					throw new IllegalArgumentException( "Cannot parse given string into array of strings."
																+ " Outside of quote, but neither whitespace, comma, array end, nor null found." );
				}
			}
			else if ( c == '\\' && i + 2 < len && (charSequence.charAt( i + 1 ) == '\\' || charSequence.charAt( i + 1 ) == '"')) {
				c = charSequence.charAt( ++i );
			}
			// If there is ever a null-pointer here, the if-else logic before is incomplete
			sb.append( c );
		}
		//noinspection unchecked
		final T[] result = (T[]) Array.newInstance( getElementJavaType().getJavaTypeClass(), lst.size() );
		for ( int i = 0; i < result.length; i ++ ) {
			if ( lst.get( i ) != null ) {
				result[i] = getElementJavaType().fromString( lst.get( i ) );
			}
		}
		return result;
	}

	@Override
	public <X> X unwrap(T[] value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		if ( type.isInstance( value ) ) {
			//noinspection unchecked
			return (X) value;
		}
		else if ( type == byte[].class ) {
			// byte[] can only be requested if the value should be serialized
			return (X) SerializationHelper.serialize( value );
		}
		else if ( type == BinaryStream.class ) {
			// BinaryStream can only be requested if the value should be serialized
			//noinspection unchecked
			return (X) new BinaryStreamImpl( SerializationHelper.serialize( value ) );
		}
		else if ( type.isArray() ) {
			final Class<?> preferredJavaTypeClass = type.getComponentType();
			final Object[] unwrapped = (Object[]) Array.newInstance( preferredJavaTypeClass, value.length );
			for ( int i = 0; i < value.length; i++ ) {
				unwrapped[i] = getElementJavaType().unwrap( value[i], preferredJavaTypeClass, options );
			}
			//noinspection unchecked
			return (X) unwrapped;
		}

		throw unknownUnwrap( type );
	}

	@Override
	public <X> T[] wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		if ( value instanceof java.sql.Array ) {
			try {
				//noinspection unchecked
				value = (X) ( (java.sql.Array) value ).getArray();
			}
			catch ( SQLException ex ) {
				// This basically shouldn't happen unless you've lost connection to the database.
				throw new HibernateException( ex );
			}
		}

		if ( value instanceof Object[] ) {
			final Object[] raw = (Object[]) value;
			final Class<T> componentClass = getElementJavaType().getJavaTypeClass();
			//noinspection unchecked
			final T[] wrapped = (T[]) java.lang.reflect.Array.newInstance( componentClass, raw.length );
			if ( componentClass.isAssignableFrom( value.getClass().getComponentType() ) ) {
				for (int i = 0; i < raw.length; i++) {
					//noinspection unchecked
					wrapped[i] = (T) raw[i];
				}
			}
			else {
				for ( int i = 0; i < raw.length; i++ ) {
					wrapped[i] = getElementJavaType().wrap( raw[i], options );
				}
			}
			return wrapped;
		}
		else if ( value instanceof byte[] ) {
			// When the value is a byte[], this is a deserialization request
			//noinspection unchecked
			return (T[]) SerializationHelper.deserialize( (byte[]) value );
		}
		else if ( value instanceof BinaryStream ) {
			// When the value is a BinaryStream, this is a deserialization request
			//noinspection unchecked
			return (T[]) SerializationHelper.deserialize( ( (BinaryStream) value ).getBytes() );
		}

		throw unknownWrap( value.getClass() );
	}

	private static class ArrayMutabilityPlan<T> implements MutabilityPlan<T[]> {

		private final Class<T> componentClass;
		private final MutabilityPlan<T> componentPlan;

		public ArrayMutabilityPlan(JavaType<T> baseDescriptor) {
			this.componentClass = baseDescriptor.getJavaTypeClass();
			this.componentPlan = baseDescriptor.getMutabilityPlan();
		}

		@Override
		public boolean isMutable() {
			return true;
		}

		@Override
		public T[] deepCopy(T[] value) {
			if ( value == null ) {
				return null;
			}
			//noinspection unchecked
			T[] copy = (T[]) Array.newInstance( componentClass, value.length );
			for ( int i = 0; i < value.length; i ++ ) {
				copy[ i ] = componentPlan.deepCopy( value[ i ] );
			}
			return copy;
		}

		@Override
		public Serializable disassemble(T[] value, SharedSessionContract session) {
			return deepCopy( value );
		}

		@Override
		public T[] assemble(Serializable cached, SharedSessionContract session) {
			//noinspection unchecked
			return deepCopy( (T[]) cached );
		}

	}
}
