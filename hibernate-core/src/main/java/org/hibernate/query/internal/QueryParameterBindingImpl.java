/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.internal;

import java.util.Collection;
import java.util.Iterator;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressible;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.query.BindableType;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterBindingValidator;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.type.descriptor.java.CoercionException;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.JavaTypeHelper;
import org.hibernate.type.descriptor.java.TemporalJavaType;
import org.hibernate.type.spi.TypeConfiguration;

import jakarta.persistence.TemporalType;

/**
 * The standard Hibernate QueryParameterBinding implementation
 *
 * @author Steve Ebersole
 */
public class QueryParameterBindingImpl<T> implements QueryParameterBinding<T>, JavaType.CoercionContext {
	private final QueryParameter<T> queryParameter;
	private final SessionFactoryImplementor sessionFactory;

	private boolean isBound;
	private boolean isMultiValued;

	private BindableType<? extends T> bindType;
	private MappingModelExpressible<T> type;
	private TemporalType explicitTemporalPrecision;

	private T bindValue;
	private Collection<? extends T> bindValues;

	/**
	 * Used by {@link org.hibernate.procedure.ProcedureCall}
	 */
	protected QueryParameterBindingImpl(
			QueryParameter<T> queryParameter,
			SessionFactoryImplementor sessionFactory) {
		this( queryParameter, sessionFactory, queryParameter.getHibernateType() );
	}

	/**
	 * Used by Query (SQM) and NativeQuery
	 */
	public QueryParameterBindingImpl(
			QueryParameter<T> queryParameter,
			SessionFactoryImplementor sessionFactory,
			BindableType<T> bindType) {
		this.queryParameter = queryParameter;
		this.sessionFactory = sessionFactory;
		this.bindType = bindType;
	}

	@Override
	public BindableType<? extends T> getBindType() {
		return bindType;
	}

	@Override
	public TemporalType getExplicitTemporalPrecision() {
		return explicitTemporalPrecision;
	}

	@Override
	public boolean isBound() {
		return isBound;
	}

	@Override
	public boolean isMultiValued() {
		return isMultiValued;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// single-valued binding support

	@Override
	public T getBindValue() {
		if ( isMultiValued ) {
			throw new IllegalStateException( "Binding is multi-valued; illegal call to #getBindValue" );
		}

		return bindValue;
	}

	@Override
	public void setBindValue(T value, boolean resolveJdbcTypeIfNecessary) {
		if ( handleAsMultiValue( value, null ) ) {
			return;
		}

		if ( ! sessionFactory.getSessionFactoryOptions().getJpaCompliance().isLoadByIdComplianceEnabled() ) {
			try {
				if ( bindType != null ) {
					value = coerce( value, bindType );
				}
				else if ( queryParameter.getHibernateType() != null ) {
					value = coerce( value, queryParameter.getHibernateType() );
				}
			}
			catch (CoercionException ce) {
				throw new IllegalArgumentException(
						String.format(
								"Parameter value [%s] did not match expected type [%s ]",
								value,
								bindType
						),
						ce
				);
			}
		}

		validate( value );

		if ( resolveJdbcTypeIfNecessary && bindType == null && value == null ) {
			bindType = getTypeConfiguration().getBasicTypeRegistry().getRegisteredType( "null" );
		}
		bindValue( value );
	}

	private T coerce(T value, BindableType<? extends T> parameterType) {
		if ( value == null ) {
			return null;
		}

		final SqmExpressible<? extends T> sqmExpressible = parameterType.resolveExpressible( sessionFactory );
		assert sqmExpressible != null;
		return sqmExpressible.getExpressibleJavaType().coerce( value, this );
	}

	private boolean handleAsMultiValue(T value, BindableType<T> bindableType) {
		if ( ! queryParameter.allowsMultiValuedBinding() ) {
			return false;
		}

		if ( value == null ) {
			return false;
		}

		if ( value instanceof Collection
				&& ( bindableType != null && !bindableType.getBindableJavaType().isInstance( value )
				|| ( bindableType == null && !isRegisteredAsBasicType( value.getClass() ) ) ) ) {
			//noinspection unchecked
			setBindValues( (Collection<T>) value );
			return true;
		}

		return false;
	}

	private boolean isRegisteredAsBasicType(Class<?> valueClass) {
		return getTypeConfiguration().getBasicTypeForJavaType( valueClass ) != null;
	}

	private void bindValue(T value) {
		this.isBound = true;
		this.bindValue = value;

		if ( bindType == null ) {
			if ( value != null ) {
				this.bindType = sessionFactory.getMappingMetamodel().resolveParameterBindType( value );
			}
		}
	}

	@Override
	public void setBindValue(T value, BindableType<T> clarifiedType) {
		if ( handleAsMultiValue( value, clarifiedType ) ) {
			return;
		}

		if ( clarifiedType != null ) {
			this.bindType = clarifiedType;
		}

		if ( bindType != null ) {
			value = coerce( value, bindType );
		}
		else if ( queryParameter.getHibernateType() != null ) {
			value = coerce( value, queryParameter.getHibernateType() );
		}

		validate( value, clarifiedType );

		bindValue( value );
	}

	@Override
	public void setBindValue(T value, TemporalType temporalTypePrecision) {
		if ( handleAsMultiValue( value, null ) ) {
			return;
		}

		if ( bindType == null ) {
			bindType = queryParameter.getHibernateType();
		}

		if ( ! sessionFactory.getSessionFactoryOptions().getJpaCompliance().isLoadByIdComplianceEnabled() ) {
			if ( bindType != null ) {
				try {
					value = coerce( value, bindType );
				}
				catch (CoercionException ex) {
					throw new IllegalArgumentException(
							String.format(
									"Parameter value [%s] did not match expected type [%s (%s)]",
									value,
									bindType,
									temporalTypePrecision == null ? "n/a" : temporalTypePrecision.name()
							),
							ex
					);
				}
			}
			else if ( queryParameter.getHibernateType() != null ) {
				value = coerce( value, queryParameter.getHibernateType() );
			}
		}

		validate( value, temporalTypePrecision );

		bindValue( value );
		setExplicitTemporalPrecision( temporalTypePrecision );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// multi-valued binding support

	@Override
	public Collection<? extends T> getBindValues() {
		if ( !isMultiValued ) {
			throw new IllegalStateException( "Binding is not multi-valued; illegal call to #getBindValues" );
		}

		return bindValues;
	}

	@Override
	public void setBindValues(Collection<? extends T> values) {
		this.isBound = true;
		this.isMultiValued = true;

		this.bindValue = null;
		this.bindValues = values;

		final Iterator<? extends T> iterator = values.iterator();
		T value = null;
		while ( value == null && iterator.hasNext() ) {
			value = iterator.next();
		}

		if ( bindType == null && value != null ) {
			this.bindType = sessionFactory.getMappingMetamodel().resolveParameterBindType( value );
		}

	}

	@Override
	public void setBindValues(Collection<? extends T> values, BindableType<T> clarifiedType) {
		if ( clarifiedType != null ) {
			this.bindType = clarifiedType;
		}
		setBindValues( values );
	}

	@Override
	public void setBindValues(
			Collection<? extends T> values,
			TemporalType temporalTypePrecision,
			TypeConfiguration typeConfiguration) {
		setBindValues( values );
		setExplicitTemporalPrecision( temporalTypePrecision );
	}

	private void setExplicitTemporalPrecision(TemporalType temporalTypePrecision) {
		if ( bindType == null || JavaTypeHelper.isTemporal( determineJavaType( bindType ) ) ) {
			this.bindType = BindingTypeHelper.INSTANCE.resolveTemporalPrecision(
					temporalTypePrecision,
					bindType,
					sessionFactory
			);
		}

		this.explicitTemporalPrecision = temporalTypePrecision;
	}

	private JavaType<? extends T> determineJavaType(BindableType<? extends T> bindType) {
		final SqmExpressible<? extends T> sqmExpressible = bindType.resolveExpressible( sessionFactory );
		assert sqmExpressible != null;

		return sqmExpressible.getExpressibleJavaType();
	}

	@Override
	public MappingModelExpressible<T> getType() {
		return type;
	}

	@Override @SuppressWarnings("unchecked")
	public boolean setType(MappingModelExpressible<T> type) {
		this.type = type;
		// If the bind type is undetermined or the given type is a model part, then we try to apply a new bind type
		if ( bindType == null || bindType.getBindableJavaType() == Object.class || type instanceof ModelPart ) {
			if ( type instanceof BindableType<?> ) {
				final boolean changed = bindType != null && type != bindType;
				this.bindType = (BindableType<T>) type;
				return changed;
			}
			else if ( type instanceof BasicValuedMapping ) {
				final JdbcMapping jdbcMapping = ( (BasicValuedMapping) type ).getJdbcMapping();
				if ( jdbcMapping instanceof BindableType<?> ) {
					final boolean changed = bindType != null && jdbcMapping != bindType;
					this.bindType = (BindableType<T>) jdbcMapping;
					return changed;
				}
			}
		}
		return false;
	}

	private void validate(T value) {
		QueryParameterBindingValidator.INSTANCE.validate( getBindType(), value, sessionFactory );
	}

	private void validate(T value, BindableType<?> clarifiedType) {
		QueryParameterBindingValidator.INSTANCE.validate( clarifiedType, value, sessionFactory );
	}

	private void validate(T value, TemporalType clarifiedTemporalType) {
		QueryParameterBindingValidator.INSTANCE.validate( getBindType(), value, clarifiedTemporalType, sessionFactory );
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return sessionFactory.getTypeConfiguration();
	}
}
