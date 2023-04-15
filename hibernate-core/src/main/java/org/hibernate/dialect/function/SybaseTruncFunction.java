/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect.function;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.query.ReturnableType;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.TemporalUnit;
import org.hibernate.query.sqm.function.SelfRenderingSqmFunction;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.expression.SqmExtractUnit;
import org.hibernate.query.sqm.tree.expression.SqmLiteral;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.type.spi.TypeConfiguration;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Custom {@link TruncFunction} for Sybase which uses a dialect-specific emulation function for datetimes
 *
 * @author Marco Belladelli
 */
public class SybaseTruncFunction extends TruncFunction {
	private final SybaseDateTruncEmulation dateTruncEmulation;

	public SybaseTruncFunction(TypeConfiguration typeConfiguration) {
		super(
				"sign(?1)*floor(abs(?1))",
				"sign(?1)*floor(abs(?1)*power(10,?2))/power(10,?2)",
				null,
				null,
				typeConfiguration
		);
		this.dateTruncEmulation = new SybaseDateTruncEmulation( "convert", typeConfiguration );
	}

	@Override
	protected <T> SelfRenderingSqmFunction<T> generateSqmFunctionExpression(
			List<? extends SqmTypedNode<?>> arguments,
			ReturnableType<T> impliedResultType,
			QueryEngine queryEngine,
			TypeConfiguration typeConfiguration) {
		final List<SqmTypedNode<?>> args = new ArrayList<>( arguments );
		if ( arguments.size() == 2 && arguments.get( 1 ) instanceof SqmExtractUnit ) {
			// datetime truncation
			return dateTruncEmulation.generateSqmFunctionExpression(
					arguments,
					impliedResultType,
					queryEngine,
					typeConfiguration
			);
		}
		// numeric truncation
		return new SelfRenderingSqmFunction<>(
				this,
				numericRenderingSupport,
				args,
				impliedResultType,
				TruncArgumentsValidator.NUMERIC_VALIDATOR,
				getReturnTypeResolver(),
				queryEngine.getCriteriaBuilder(),
				getName()
		);
	}

	/**
	 * Custom {@link DateTruncEmulation} that uses convert instead of format for Sybase
	 *
	 * @author Marco Belladelli
	 * @see <a href="https://infocenter.sybase.com/help/index.jsp?topic=/com.sybase.infocenter.dc36271.1600/doc/html/san1393050437990.html">Sybase Documentation</a>
	 */
	private static class SybaseDateTruncEmulation extends DateTruncEmulation {
		public SybaseDateTruncEmulation(
				String toDateFunction,
				TypeConfiguration typeConfiguration) {
			super( toDateFunction, typeConfiguration );
		}

		@Override
		public void render(
				SqlAppender sqlAppender,
				List<? extends SqlAstNode> sqlAstArguments,
				SqlAstTranslator<?> walker) {
			sqlAppender.appendSql( toDateFunction );
			sqlAppender.append( '(' );
			sqlAppender.append( "datetime,substring(convert(varchar," );
			sqlAstArguments.get( 0 ).accept( walker );
			sqlAppender.append( ",21),1,17" );
			if ( sqlAstArguments.size() > 1 ) {
				sqlAppender.append( "-len(" );
				sqlAstArguments.get( 1 ).accept( walker );
				sqlAppender.append( "))+" );
				sqlAstArguments.get( 1 ).accept( walker );
			}
			else {
				sqlAppender.append( ')' );
			}
			sqlAppender.append( ",21)" );
		}

		@Override
		protected <T> SelfRenderingSqmFunction<T> generateSqmFunctionExpression(
				List<? extends SqmTypedNode<?>> arguments,
				ReturnableType<T> impliedResultType,
				QueryEngine queryEngine,
				TypeConfiguration typeConfiguration) {
			final NodeBuilder nodeBuilder = queryEngine.getCriteriaBuilder();
			final TemporalUnit temporalUnit = ( (SqmExtractUnit<?>) arguments.get( 1 ) ).getUnit();
			final String literal;
			switch ( temporalUnit ) {
				case YEAR:
					literal = "/01/01 00:00:00";
					break;
				case MONTH:
					literal = "/01 00:00:00";
					break;
				case DAY:
					literal = " 00:00:00";
					break;
				case HOUR:
					literal = ":00:00";
					break;
				case MINUTE:
					literal = ":00";
					break;
				case SECOND:
					literal = null;
					break;
				default:
					throw new UnsupportedOperationException( "Temporal unit not supported [" + temporalUnit + "]" );
			}

			return new SelfRenderingSqmFunction<>(
					this,
					this,
					literal == null ?
							singletonList( arguments.get( 0 ) ) :
							asList(
									arguments.get( 0 ),
									new SqmLiteral<>(
											literal,
											typeConfiguration.getBasicTypeForJavaType( String.class ),
											nodeBuilder
									)
							),
					impliedResultType,
					null,
					getReturnTypeResolver(),
					nodeBuilder,
					getName()
			);
		}
	}
}
