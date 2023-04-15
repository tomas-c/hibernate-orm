/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.Bindable;
import org.hibernate.metamodel.mapping.EntityAssociationMapping;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressible;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.type.JavaObjectType;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.query.IllegalQueryOperationException;
import org.hibernate.query.IllegalSelectQueryException;
import org.hibernate.spi.NavigablePath;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.sqm.SqmQuerySource;
import org.hibernate.query.sqm.spi.JdbcParameterBySqmParameterAccess;
import org.hibernate.query.sqm.spi.SqmParameterMappingModelResolutionAccess;
import org.hibernate.query.sqm.tree.SqmDmlStatement;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.expression.JpaCriteriaParameter;
import org.hibernate.query.sqm.tree.expression.SqmJpaCriteriaParameterWrapper;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.jpa.ParameterCollector;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.sql.ast.SqlTreeCreationException;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.exec.internal.JdbcParameterBindingImpl;
import org.hibernate.sql.exec.internal.JdbcParameterBindingsImpl;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Helper utilities for dealing with SQM
 *
 * @author Steve Ebersole
 */
public class SqmUtil {
	private SqmUtil() {
	}

	public static boolean isSelect(SqmStatement<?> sqm) {
		return sqm instanceof SqmSelectStatement;
	}

	public static boolean isMutation(SqmStatement<?> sqm) {
		return sqm instanceof SqmDmlStatement;
	}

	public static void verifyIsSelectStatement(SqmStatement<?> sqm, String hqlString) {
		if ( ! isSelect( sqm ) ) {
			throw new IllegalSelectQueryException(
					String.format(
							Locale.ROOT,
							"Expecting a SELECT Query [%s], but found %s",
							SqmSelectStatement.class.getName(),
							sqm.getClass().getName()
					),
					hqlString
			);
		}
	}

	public static void verifyIsNonSelectStatement(SqmStatement<?> sqm, String hqlString) {
		if ( ! isMutation( sqm ) ) {
			throw expectingNonSelect( sqm, hqlString );
		}
	}

	public static IllegalQueryOperationException expectingNonSelect(SqmStatement<?> sqm, String hqlString) {
		return new IllegalQueryOperationException(
				String.format(
						Locale.ROOT,
						"Expecting a non-SELECT Query [%s], but found %s",
						SqmDmlStatement.class.getName(),
						sqm.getClass().getName()
				),
				hqlString,
				null
		);
	}

	public static Map<QueryParameterImplementor<?>, Map<SqmParameter<?>, List<List<JdbcParameter>>>> generateJdbcParamsXref(
			DomainParameterXref domainParameterXref,
			JdbcParameterBySqmParameterAccess jdbcParameterBySqmParameterAccess) {
		if ( domainParameterXref == null || !domainParameterXref.hasParameters() ) {
			return Collections.emptyMap();
		}

		final int queryParameterCount = domainParameterXref.getQueryParameterCount();
		final Map<QueryParameterImplementor<?>, Map<SqmParameter<?>, List<List<JdbcParameter>>>> result = new IdentityHashMap<>( queryParameterCount );

		for ( Map.Entry<QueryParameterImplementor<?>, List<SqmParameter<?>>> entry : domainParameterXref.getSqmParamByQueryParam().entrySet() ) {
			final QueryParameterImplementor<?> queryParam = entry.getKey();
			final List<SqmParameter<?>> sqmParams = entry.getValue();

			final Map<SqmParameter<?>, List<List<JdbcParameter>>> sqmParamMap = result.computeIfAbsent(
					queryParam,
					qp -> new IdentityHashMap<>( sqmParams.size() )
			);

			for ( SqmParameter<?> sqmParam : sqmParams ) {
				sqmParamMap.put( sqmParam, jdbcParameterBySqmParameterAccess.getJdbcParamsBySqmParam().get( sqmParam ) );

				final List<SqmParameter<?>> expansions = domainParameterXref.getExpansions( sqmParam );
				if ( ! expansions.isEmpty() ) {
					for ( SqmParameter<?> expansion : expansions ) {
						sqmParamMap.put( expansion, jdbcParameterBySqmParameterAccess.getJdbcParamsBySqmParam().get( expansion ) );
						result.put( queryParam, sqmParamMap );
					}
				}
			}
		}

		return result;
	}

//	public static JdbcParameterBindings buildJdbcParameterBindings(
//			SqmStatement sqmStatement,
//			JdbcParameterBySqmParameterAccess sqmInterpretation,
//			ExecutionContext executionContext) {
//		final DomainParameterXref domainParameterXref = DomainParameterXref.from( sqmStatement );
//		final Map<QueryParameterImplementor<?>, Map<SqmParameter, List<JdbcParameter>>> jdbcParamsXref =
//				generateJdbcParamsXref( domainParameterXref, sqmInterpretation );
//		return createJdbcParameterBindings(
//				executionContext.getDomainParameterBindingContext().getQueryParameterBindings(),
//				domainParameterXref,
//				jdbcParamsXref,
//				executionContext.getSession()
//		);
//	}

//	public static JdbcParameterBindings buildJdbcParameterBindings(
//			SqmStatement sqmStatement,
//			Map<QueryParameterImplementor<?>, Map<SqmParameter, List<JdbcParameter>>> jdbcParamsXref,
//			ExecutionContext executionContext) {
//		final DomainParameterXref domainParameterXref = DomainParameterXref.from( sqmStatement );
//		return createJdbcParameterBindings(
//				executionContext.getDomainParameterBindingContext().getQueryParameterBindings(),
//				domainParameterXref,
//				jdbcParamsXref,
//				executionContext.getSession()
//		);
//	}

//	public static JdbcParameterBindings buildJdbcParameterBindings(
//			DomainParameterXref domainParameterXref,
//			Map<QueryParameterImplementor<?>, Map<SqmParameter, List<JdbcParameter>>> jdbcParamsXref,
//			ExecutionContext executionContext) {
//		return createJdbcParameterBindings(
//				executionContext.getDomainParameterBindingContext().getQueryParameterBindings(),
//				domainParameterXref,
//				jdbcParamsXref,
//				executionContext.getSession()
//		);
//	}

	public static JdbcParameterBindings createJdbcParameterBindings(
			QueryParameterBindings domainParamBindings,
			DomainParameterXref domainParameterXref,
			Map<QueryParameterImplementor<?>, Map<SqmParameter<?>, List<List<JdbcParameter>>>> jdbcParamXref,
			MappingMetamodel domainModel,
			Function<NavigablePath, TableGroup> tableGroupLocator,
			SqmParameterMappingModelResolutionAccess mappingModelResolutionAccess,
			SharedSessionContractImplementor session) {
		final JdbcParameterBindings jdbcParameterBindings = new JdbcParameterBindingsImpl(
				domainParameterXref.getSqmParameterCount()
		);

		for ( Map.Entry<QueryParameterImplementor<?>, List<SqmParameter<?>>> entry :
				domainParameterXref.getSqmParamByQueryParam().entrySet() ) {
			final QueryParameterImplementor<?> queryParam = entry.getKey();
			final List<SqmParameter<?>> sqmParameters = entry.getValue();

			final QueryParameterBinding<?> domainParamBinding = domainParamBindings.getBinding( queryParam );

			final Map<SqmParameter<?>, List<List<JdbcParameter>>> jdbcParamMap = jdbcParamXref.get( queryParam );
			for ( SqmParameter<?> sqmParameter : sqmParameters ) {
				final MappingModelExpressible resolvedMappingModelType = mappingModelResolutionAccess
						.getResolvedMappingModelType( sqmParameter );
				if ( resolvedMappingModelType != null ) {
					domainParamBinding.setType( resolvedMappingModelType );
				}
				final Bindable parameterType = determineParameterType(
						domainParamBinding,
						queryParam,
						sqmParameters,
						mappingModelResolutionAccess,
						session.getFactory()
				);

				final List<List<JdbcParameter>> jdbcParamsBinds = jdbcParamMap.get( sqmParameter );
				if ( jdbcParamsBinds == null ) {
					// This can happen when a group or order by item expression, that contains parameters,
					// is replaced with an alias reference expression, which can happen for JPA Criteria queries
					continue;
				}
				if ( !domainParamBinding.isBound() ) {
					for ( int i = 0; i < jdbcParamsBinds.size(); i++ ) {
						final List<JdbcParameter> jdbcParams = jdbcParamsBinds.get( i );
						parameterType.forEachJdbcType(
								(position, jdbcMapping) -> {
									jdbcParameterBindings.addBinding(
											jdbcParams.get( position ),
											new JdbcParameterBindingImpl( jdbcMapping, null )
									);
								}
						);
					}
				}
				else if ( domainParamBinding.isMultiValued() ) {
					final Collection<?> bindValues = domainParamBinding.getBindValues();
					final Iterator<?> valueItr = bindValues.iterator();

					// the original SqmParameter is the one we are processing.. create a binding for it..
					for ( int i = 0; i < jdbcParamsBinds.size(); i++ ) {
						final List<JdbcParameter> jdbcParams = jdbcParamsBinds.get( i );
						createValueBindings(
								jdbcParameterBindings,
								queryParam,
								domainParamBinding,
								parameterType,
								jdbcParams,
								valueItr.next(),
								tableGroupLocator,
								session
						);
					}

					// an then one for each of the expansions
					final List<SqmParameter<?>> expansions = domainParameterXref.getExpansions( sqmParameter );
					assert expansions.size() == bindValues.size() - 1;
					int expansionPosition = 0;
					while ( valueItr.hasNext() ) {
						final SqmParameter<?> expansionSqmParam = expansions.get( expansionPosition++ );
						final List<List<JdbcParameter>> jdbcParamBinds = jdbcParamMap.get( expansionSqmParam );
						for ( int i = 0; i < jdbcParamBinds.size(); i++ ) {
							List<JdbcParameter> expansionJdbcParams = jdbcParamBinds.get( i );
							createValueBindings(
									jdbcParameterBindings,
									queryParam, domainParamBinding,
									parameterType,
									expansionJdbcParams,
									valueItr.next(),
									tableGroupLocator,
									session
							);
						}
					}
				}
				else if ( domainParamBinding.getBindValue() == null ) {
					for ( int i = 0; i < jdbcParamsBinds.size(); i++ ) {
						final List<JdbcParameter> jdbcParams = jdbcParamsBinds.get( i );
						for ( int j = 0; j < jdbcParams.size(); j++ ) {
							final JdbcParameter jdbcParameter = jdbcParams.get( j );
							jdbcParameterBindings.addBinding(
									jdbcParameter,
									new JdbcParameterBindingImpl( null, null )
							);
						}
					}
				}
				else {
					final JdbcMapping jdbcMapping;
					if ( domainParamBinding.getType() instanceof JdbcMapping ) {
						jdbcMapping = (JdbcMapping) domainParamBinding.getType();
					}
					else if ( domainParamBinding.getBindType() instanceof BasicValuedMapping ) {
						jdbcMapping = ( (BasicValuedMapping) domainParamBinding.getType() ).getJdbcMapping();
					}
					else {
						jdbcMapping = null;
					}

					final BasicValueConverter valueConverter = jdbcMapping == null ? null : jdbcMapping.getValueConverter();
					if ( valueConverter != null ) {
						final Object convertedValue = valueConverter.toRelationalValue( domainParamBinding.getBindValue() );

						for ( int i = 0; i < jdbcParamsBinds.size(); i++ ) {
							final List<JdbcParameter> jdbcParams = jdbcParamsBinds.get( i );
							assert jdbcParams.size() == 1;
							final JdbcParameter jdbcParameter = jdbcParams.get( 0 );
							jdbcParameterBindings.addBinding(
									jdbcParameter,
									new JdbcParameterBindingImpl( jdbcMapping, convertedValue )
							);
						}

						continue;
					}

					final Object bindValue = domainParamBinding.getBindValue();
					for ( int i = 0; i < jdbcParamsBinds.size(); i++ ) {
						final List<JdbcParameter> jdbcParams = jdbcParamsBinds.get( i );
						createValueBindings(
								jdbcParameterBindings,
								queryParam,
								domainParamBinding,
								parameterType,
								jdbcParams,
								bindValue,
								tableGroupLocator,
								session
						);
					}
				}
			}
		}

		return jdbcParameterBindings;
	}

	private static void createValueBindings(
			JdbcParameterBindings jdbcParameterBindings,
			QueryParameterImplementor<?> domainParam,
			QueryParameterBinding<?> domainParamBinding,
			Bindable parameterType,
			List<JdbcParameter> jdbcParams,
			Object bindValue,
			Function<NavigablePath, TableGroup> tableGroupLocator,
			SharedSessionContractImplementor session) {
		if ( parameterType == null ) {
			throw new SqlTreeCreationException( "Unable to interpret mapping-model type for Query parameter : " + domainParam );
		}
		else if ( parameterType instanceof PluralAttributeMapping ) {
			// Default to the collection element
			parameterType = ( (PluralAttributeMapping) parameterType ).getElementDescriptor();
		}

		if ( parameterType instanceof EntityIdentifierMapping ) {
			final EntityIdentifierMapping identifierMapping = (EntityIdentifierMapping) parameterType;
			final EntityMappingType entityMapping = identifierMapping.findContainingEntityMapping();
			if ( entityMapping.getRepresentationStrategy().getInstantiator().isInstance( bindValue, session.getFactory() ) ) {
				bindValue = identifierMapping.getIdentifierIfNotUnsaved( bindValue, session );
			}
		}
		else if ( parameterType instanceof EntityMappingType ) {
			final EntityIdentifierMapping identifierMapping = ( (EntityMappingType) parameterType ).getIdentifierMapping();
			final EntityMappingType entityMapping = identifierMapping.findContainingEntityMapping();
			parameterType = identifierMapping;
			if ( entityMapping.getRepresentationStrategy().getInstantiator().isInstance( bindValue, session.getFactory() ) ) {
				bindValue = identifierMapping.getIdentifierIfNotUnsaved( bindValue, session );
			}
		}
		else if ( parameterType instanceof EntityAssociationMapping ) {
			EntityAssociationMapping association = (EntityAssociationMapping) parameterType;
			if ( association.getSideNature() == ForeignKeyDescriptor.Nature.TARGET ) {
				// If the association is the target, we must use the identifier of the EntityMappingType
				bindValue = association.getAssociatedEntityMappingType().getIdentifierMapping()
						.getIdentifier( bindValue );
				parameterType = association.getAssociatedEntityMappingType().getIdentifierMapping();
			}
			else {
				bindValue = association.getForeignKeyDescriptor().getAssociationKeyFromSide(
						bindValue,
						association.getSideNature().inverse(),
						session
				);
				parameterType = association.getForeignKeyDescriptor();
			}
		}
		else if ( parameterType instanceof JavaObjectType ) {
			parameterType = domainParamBinding.getType();
		}

		int offset = jdbcParameterBindings.registerParametersForEachJdbcValue(
				bindValue,
				parameterType,
				jdbcParams,
				session
		);
		assert offset == jdbcParams.size();
	}

	public static Bindable determineParameterType(
			QueryParameterBinding<?> binding,
			QueryParameterImplementor<?> parameter,
			List<SqmParameter<?>> sqmParameters,
			SqmParameterMappingModelResolutionAccess mappingModelResolutionAccess,
			SessionFactoryImplementor sessionFactory) {
		if ( binding.getBindType() instanceof Bindable ) {
			return (Bindable) binding.getBindType();
		}

		if ( parameter.getHibernateType() instanceof Bindable ) {
			return (Bindable) parameter.getHibernateType();
		}

		if ( binding.getType() != null ) {
			return binding.getType();
		}

		for ( int i = 0; i < sqmParameters.size(); i++ ) {
			final MappingModelExpressible<?> mappingModelType = mappingModelResolutionAccess
					.getResolvedMappingModelType( sqmParameters.get( i ) );
			if ( mappingModelType != null ) {
				return mappingModelType;
			}
		}

		final TypeConfiguration typeConfiguration = sessionFactory.getTypeConfiguration();

		// assume we have (or can create) a mapping for the parameter's Java type
		return typeConfiguration.standardBasicTypeForJavaType( parameter.getParameterType() );
	}

	public static SqmStatement.ParameterResolutions resolveParameters(SqmStatement<?> statement) {
		if ( statement.getQuerySource() == SqmQuerySource.CRITERIA ) {
			final CriteriaParameterCollector parameterCollector = new CriteriaParameterCollector();

			ParameterCollector.collectParameters(
					statement,
					parameterCollector::process,
					statement.nodeBuilder().getServiceRegistry()
			);

			return parameterCollector.makeResolution();
		}
		else {
			return new SqmStatement.ParameterResolutions() {
				@Override
				public Set<SqmParameter<?>> getSqmParameters() {
					return statement.getSqmParameters();
				}

				@Override
				public Map<JpaCriteriaParameter<?>, SqmJpaCriteriaParameterWrapper<?>> getJpaCriteriaParamResolutions() {
					return Collections.emptyMap();
				}
			};
		}
	}

	private static class CriteriaParameterCollector {
		private Set<SqmParameter<?>> sqmParameters;
		private Map<JpaCriteriaParameter<?>, List<SqmJpaCriteriaParameterWrapper<?>>> jpaCriteriaParamResolutions;

		public void process(SqmParameter<?> parameter) {
			if ( sqmParameters == null ) {
				sqmParameters = new HashSet<>();
			}

			if ( parameter instanceof SqmJpaCriteriaParameterWrapper<?> ) {
				if ( jpaCriteriaParamResolutions == null ) {
					jpaCriteriaParamResolutions = new IdentityHashMap<>();
				}

				final SqmJpaCriteriaParameterWrapper<?> wrapper = (SqmJpaCriteriaParameterWrapper<?>) parameter;
				final JpaCriteriaParameter<?> criteriaParameter = wrapper.getJpaCriteriaParameter();

				final List<SqmJpaCriteriaParameterWrapper<?>> sqmParametersForCriteriaParameter = jpaCriteriaParamResolutions.computeIfAbsent(
						criteriaParameter,
						jcp -> new ArrayList<>()
				);

				sqmParametersForCriteriaParameter.add( wrapper );
				sqmParameters.add( wrapper );
			}
			else if ( parameter instanceof JpaCriteriaParameter ) {
				throw new UnsupportedOperationException();
//				final JpaCriteriaParameter<?> criteriaParameter = (JpaCriteriaParameter<?>) parameter;
//
//				if ( jpaCriteriaParamResolutions == null ) {
//					jpaCriteriaParamResolutions = new IdentityHashMap<>();
//				}
//
//				final List<SqmJpaCriteriaParameterWrapper<?>> sqmParametersForCriteriaParameter = jpaCriteriaParamResolutions.computeIfAbsent(
//						criteriaParameter,
//						jcp -> new ArrayList<>()
//				);
//
//				final SqmJpaCriteriaParameterWrapper<?> wrapper = new SqmJpaCriteriaParameterWrapper(
//						criteriaParameter.getHibernateType(),
//						criteriaParameter,
//						criteriaParameter.nodeBuilder()
//				);
//
//				sqmParametersForCriteriaParameter.add( wrapper );
//				sqmParameters.add( wrapper );
			}
			else {
				sqmParameters.add( parameter );
			}
		}

		private SqmStatement.ParameterResolutions makeResolution() {
			return new ParameterResolutionsImpl(
					sqmParameters == null ? Collections.emptySet() : sqmParameters,
					jpaCriteriaParamResolutions == null ? Collections.emptyMap() : jpaCriteriaParamResolutions
			);
		}
	}

	private static class ParameterResolutionsImpl implements SqmStatement.ParameterResolutions {
		private final Set<SqmParameter<?>> sqmParameters;
		private final Map<JpaCriteriaParameter<?>, SqmJpaCriteriaParameterWrapper<?>> jpaCriteriaParamResolutions;

		public ParameterResolutionsImpl(
				Set<SqmParameter<?>> sqmParameters,
				Map<JpaCriteriaParameter<?>, List<SqmJpaCriteriaParameterWrapper<?>>> jpaCriteriaParamResolutions) {
			this.sqmParameters = sqmParameters;

			if ( jpaCriteriaParamResolutions == null || jpaCriteriaParamResolutions.isEmpty() ) {
				this.jpaCriteriaParamResolutions = Collections.emptyMap();
			}
			else {
				this.jpaCriteriaParamResolutions = new IdentityHashMap<>( CollectionHelper.determineProperSizing( jpaCriteriaParamResolutions ) );
				for ( Map.Entry<JpaCriteriaParameter<?>, List<SqmJpaCriteriaParameterWrapper<?>>> entry : jpaCriteriaParamResolutions.entrySet() ) {
					final Iterator<SqmJpaCriteriaParameterWrapper<?>> itr = entry.getValue().iterator();
					if ( !itr.hasNext() ) {
						throw new IllegalStateException(
								"SqmJpaCriteriaParameterWrapper references for JpaCriteriaParameter [" + entry.getKey() + "] already exhausted" );
					}
					this.jpaCriteriaParamResolutions.put( entry.getKey(), itr.next() );
				}
			}
		}

		@Override
		public Set<SqmParameter<?>> getSqmParameters() {
			return sqmParameters;
		}

		@Override
		public Map<JpaCriteriaParameter<?>, SqmJpaCriteriaParameterWrapper<?>> getJpaCriteriaParamResolutions() {
			return jpaCriteriaParamResolutions;
		}
	}
}
