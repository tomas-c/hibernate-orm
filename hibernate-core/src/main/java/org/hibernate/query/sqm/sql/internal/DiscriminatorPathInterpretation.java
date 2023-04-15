/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import org.hibernate.metamodel.mapping.DiscriminatorMapping;
import org.hibernate.metamodel.mapping.EntityDiscriminatorMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.EntityValuedModelPart;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.model.domain.internal.EntityDiscriminatorSqmPath;
import org.hibernate.query.results.ResultSetMappingSqlSelection;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * SqmPathInterpretation and DomainResultProducer implementation for entity discriminator
 *
 * @author Steve Ebersole
 */
public class DiscriminatorPathInterpretation<T> extends AbstractSqmPathInterpretation<T> {
	private final Expression expression;

	public DiscriminatorPathInterpretation(
			NavigablePath navigablePath,
			DiscriminatorMapping mapping,
			TableGroup tableGroup,
			SqlAstCreationState sqlAstCreationState) {
		super( navigablePath, mapping, tableGroup );

		final JdbcMapping jdbcMappingToUse = mapping.getJdbcMapping();
		expression = getDiscriminatorMapping().resolveSqlExpression( navigablePath, jdbcMappingToUse, tableGroup, sqlAstCreationState );
	}

	public DiscriminatorPathInterpretation(
			NavigablePath navigablePath,
			EntityMappingType mapping,
			TableGroup tableGroup,
			SqlAstCreationState sqlAstCreationState) {
		super( navigablePath, mapping.getDiscriminatorMapping(), tableGroup );

		final JdbcMapping jdbcMappingToUse = mapping.getDiscriminatorMapping().getJdbcMapping();
		expression = getDiscriminatorMapping().resolveSqlExpression( navigablePath, jdbcMappingToUse, tableGroup, sqlAstCreationState );
	}

	public static SqmPathInterpretation<?> from(
			EntityDiscriminatorSqmPath path,
			SqmToSqlAstConverter converter) {
		assert path.getEntityDescriptor().hasSubclasses();

		final NavigablePath navigablePath = path.getNavigablePath();
		final TableGroup tableGroup = converter.getFromClauseAccess().getTableGroup( navigablePath.getParent() );
		final ModelPartContainer modelPart = tableGroup.getModelPart();
		final EntityMappingType entityMapping;
		if ( modelPart instanceof EntityValuedModelPart ) {
			entityMapping = ( (EntityValuedModelPart) modelPart ).getEntityMappingType();
		}
		else {
			entityMapping = (EntityMappingType) ( (PluralAttributeMapping) modelPart ).getElementDescriptor().getPartMappingType();
		}

		return new DiscriminatorPathInterpretation<>( navigablePath, entityMapping, tableGroup, converter );
	}

	public EntityDiscriminatorMapping getDiscriminatorMapping() {
		return (EntityDiscriminatorMapping) super.getExpressionType();
	}

	@Override
	public EntityDiscriminatorMapping getExpressionType() {
		return getDiscriminatorMapping();
	}

	@Override
	public SqlSelection createSqlSelection(
			int jdbcPosition,
			int valuesArrayPosition,
			JavaType javaType,
			TypeConfiguration typeConfiguration) {
		return new ResultSetMappingSqlSelection( valuesArrayPosition, getDiscriminatorMapping() );
	}

	@Override
	public DomainResult<T> createDomainResult(String resultVariable, DomainResultCreationState creationState) {
		return getDiscriminatorMapping().createDomainResult( getNavigablePath(), getTableGroup(), resultVariable, creationState );
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		expression.accept( sqlTreeWalker );
	}
}
