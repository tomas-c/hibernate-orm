/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.internal;

import java.io.Serializable;
import java.util.Comparator;

import org.hibernate.SharedSessionContract;
import org.hibernate.annotations.Immutable;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.ImmutableMutabilityPlan;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.java.MutabilityPlanExposer;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.usertype.EnhancedUserType;
import org.hibernate.usertype.UserType;

/**
 * Adaptor between {@link UserType} and {@link org.hibernate.type.descriptor.java.JavaType}.
 *
 * @author Steve Ebersole
 */
public class UserTypeJavaTypeWrapper<J> implements BasicJavaType<J> {
	protected final UserType<J> userType;
	private final MutabilityPlan<J> mutabilityPlan;

	private final Comparator<J> comparator;

	public UserTypeJavaTypeWrapper(UserType<J> userType) {
		this.userType = userType;

		MutabilityPlan<J> resolvedMutabilityPlan = null;

		if ( userType instanceof MutabilityPlanExposer ) {
			//noinspection unchecked
			resolvedMutabilityPlan = ( (MutabilityPlanExposer<J>) userType ).getExposedMutabilityPlan();
		}

		if ( resolvedMutabilityPlan == null ) {
			final Class<J> jClass = userType.returnedClass();
			if ( jClass != null ) {
				if ( jClass.getAnnotation( Immutable.class ) != null ) {
					resolvedMutabilityPlan = ImmutableMutabilityPlan.instance();
				}
			}
		}

		if ( resolvedMutabilityPlan == null ) {
			resolvedMutabilityPlan = new MutabilityPlanWrapper<>( userType );
		}

		this.mutabilityPlan = resolvedMutabilityPlan;

		if ( userType instanceof Comparator ) {
			//noinspection unchecked
			this.comparator = ( (Comparator<J>) userType );
		}
		else {
			this.comparator = this::compare;
		}
	}

	private int compare(J first, J second) {
		if ( userType.equals( first, second ) ) {
			return 0;
		}
		return Comparator.<J, Integer>comparing( userType::hashCode ).compare( first, second );
	}

	@Override
	public MutabilityPlan<J> getMutabilityPlan() {
		return mutabilityPlan;
	}

	@Override
	public JdbcType getRecommendedJdbcType(JdbcTypeIndicators context) {
		return context.getJdbcType( userType.getSqlType() );
	}

	@Override
	public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
		return userType.getDefaultSqlLength( dialect, jdbcType );
	}

	@Override
	public int getDefaultSqlPrecision(Dialect dialect, JdbcType jdbcType) {
		return userType.getDefaultSqlPrecision( dialect, jdbcType );
	}

	@Override
	public int getDefaultSqlScale(Dialect dialect, JdbcType jdbcType) {
		return userType.getDefaultSqlScale( dialect, jdbcType );
	}

	@Override
	public Comparator<J> getComparator() {
		return comparator;
	}

	@Override
	public int extractHashCode(J value) {
		return userType.hashCode(value );
	}

	@Override
	public boolean areEqual(J one, J another) {
		return userType.equals( one, another );
	}

	@Override
	public J fromString(CharSequence string) {
		if ( userType instanceof EnhancedUserType<?> ) {
			return ( (EnhancedUserType<J>) userType ).fromStringValue( string );
		}
		throw new UnsupportedOperationException( "No support for parsing UserType values from String: " + userType );
	}

	@Override
	public String toString(J value) {
		if ( userType.returnedClass().isInstance( value ) && userType instanceof EnhancedUserType<?> ) {
			return ( (EnhancedUserType<J>) userType ).toString( value );
		}
		return value == null ? "null" : value.toString();
	}

	@Override
	public <X> X unwrap(J value, Class<X> type, WrapperOptions options) {
		final BasicValueConverter<J, Object> valueConverter = userType.getValueConverter();
		if ( value != null && !type.isInstance( value ) && valueConverter != null ) {
			final Object relationalValue = valueConverter.toRelationalValue( value );
			return valueConverter.getRelationalJavaType().unwrap( relationalValue, type, options );
		}
		//noinspection unchecked
		return (X) value;
	}

	@Override
	public <X> J wrap(X value, WrapperOptions options) {
		final BasicValueConverter<J, Object> valueConverter = userType.getValueConverter();
		if ( value != null && !userType.returnedClass().isInstance( value ) && valueConverter != null ) {
			final J domainValue = valueConverter.toDomainValue( value );
			return valueConverter.getDomainJavaType().wrap( domainValue, options );
		}
		//noinspection unchecked
		return (J) value;
	}

	@Override
	public Class<J> getJavaTypeClass() {
		return userType.returnedClass();
	}

	public static class MutabilityPlanWrapper<J> implements MutabilityPlan<J> {
		private final UserType<J> userType;

		public MutabilityPlanWrapper(UserType<J> userType) {
			this.userType = userType;
		}

		@Override
		public boolean isMutable() {
			return userType.isMutable();
		}

		@Override
		public J deepCopy(J value) {
			return userType.deepCopy( value );
		}

		@Override
		public Serializable disassemble(J value, SharedSessionContract session) {
			final Serializable disassembled = userType.disassemble( value );
			// Since UserType#disassemble is an optional operation,
			// we have to handle the fact that it could produce a null value,
			// in which case we will try to use a converter for disassembling,
			// or if that doesn't exist, simply use the domain value as is
			if ( disassembled == null && value != null ) {
				final BasicValueConverter<J, Object> valueConverter = userType.getValueConverter();
				if ( valueConverter == null ) {
					return (Serializable) value;
				}
				else {
					return valueConverter.getRelationalJavaType().getMutabilityPlan().disassemble(
							valueConverter.toRelationalValue( value ),
							session
					);
				}
			}
			return disassembled;
		}

		@Override
		public J assemble(Serializable cached, SharedSessionContract session) {
			final J assembled = userType.assemble( cached, null );
			// Since UserType#assemble is an optional operation,
			// we have to handle the fact that it could produce a null value,
			// in which case we will try to use a converter for assembling,
			// or if that doesn't exist, simply use the relational value as is
			if ( assembled == null && cached != null ) {
				final BasicValueConverter<J, Object> valueConverter = userType.getValueConverter();
				if ( valueConverter == null ) {
					return (J) cached;
				}
				else {
					return valueConverter.toDomainValue( cached );
				}
			}
			return assembled;
		}
	}
}
