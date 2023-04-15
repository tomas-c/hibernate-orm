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
import org.hibernate.query.sqm.function.FunctionRenderingSupport;
import org.hibernate.query.sqm.function.SelfRenderingSqmFunction;
import org.hibernate.query.sqm.produce.function.ArgumentsValidator;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.expression.SqmExtractUnit;
import org.hibernate.query.sqm.tree.expression.SqmLiteral;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Custom {@link TruncFunction} for Oracle which uses emulation when truncating datetimes to seconds
 *
 * @author Marco Belladelli
 */
public class OracleTruncFunction extends TruncFunction {
	private final DateTruncEmulation dateTruncEmulation;

	public OracleTruncFunction(TypeConfiguration typeConfiguration) {
		super(
				"trunc(?1)",
				"trunc(?1,?2)",
				DatetimeTrunc.TRUNC,
				null,
				typeConfiguration
		);
		this.dateTruncEmulation = new DateTruncEmulation( "to_date", typeConfiguration );
	}

	@Override
	protected <T> SelfRenderingSqmFunction<T> generateSqmFunctionExpression(
			List<? extends SqmTypedNode<?>> arguments,
			ReturnableType<T> impliedResultType,
			QueryEngine queryEngine,
			TypeConfiguration typeConfiguration) {
		final NodeBuilder nodeBuilder = queryEngine.getCriteriaBuilder();
		final List<SqmTypedNode<?>> args = new ArrayList<>( arguments );
		final FunctionRenderingSupport renderingSupport;
		final ArgumentsValidator argumentsValidator;
		if ( arguments.size() == 2 && arguments.get( 1 ) instanceof SqmExtractUnit ) {
			// datetime truncation
			renderingSupport = datetimeRenderingSupport;
			argumentsValidator = TruncArgumentsValidator.DATETIME_VALIDATOR;
			// the trunc() function requires translating the temporal_unit to a format string
			final TemporalUnit temporalUnit = ( (SqmExtractUnit<?>) arguments.get( 1 ) ).getUnit();
			final String pattern;
			switch ( temporalUnit ) {
				case YEAR:
					pattern = "YYYY";
					break;
				case MONTH:
					pattern = "MM";
					break;
				case WEEK:
					pattern = "IW";
					break;
				case DAY:
					pattern = "DD";
					break;
				case HOUR:
					pattern = "HH";
					break;
				case MINUTE:
					pattern = "MI";
					break;
				case SECOND:
					// Oracle does not support truncating to seconds with the native function, use emulation
					return dateTruncEmulation.generateSqmFunctionExpression(
							arguments,
							impliedResultType,
							queryEngine,
							typeConfiguration
					);
				default:
					throw new UnsupportedOperationException( "Temporal unit not supported [" + temporalUnit + "]" );
			}
			// replace temporal_unit parameter with translated string format literal
			args.set( 1, new SqmLiteral<>(
					pattern,
					typeConfiguration.getBasicTypeForJavaType( String.class ),
					nodeBuilder
			) );
		}
		else {
			// numeric truncation
			renderingSupport = numericRenderingSupport;
			argumentsValidator = TruncArgumentsValidator.NUMERIC_VALIDATOR;
		}

		return new SelfRenderingSqmFunction<>(
				this,
				renderingSupport,
				args,
				impliedResultType,
				argumentsValidator,
				getReturnTypeResolver(),
				queryEngine.getCriteriaBuilder(),
				getName()
		);
	}
}
