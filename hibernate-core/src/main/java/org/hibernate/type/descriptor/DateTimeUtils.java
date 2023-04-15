/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.descriptor;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.hibernate.dialect.Dialect;
import org.hibernate.sql.ast.spi.SqlAppender;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

/**
 * Utilities for dealing with date/times
 *
 * @author Steve Ebersole
 * @author Gavin King
 */
public final class DateTimeUtils {
	private DateTimeUtils() {
	}

	public static final String FORMAT_STRING_DATE = "yyyy-MM-dd";
	public static final String FORMAT_STRING_TIME_WITH_OFFSET = "HH:mm:ssXXX";
	public static final String FORMAT_STRING_TIME = "HH:mm:ss";
	public static final String FORMAT_STRING_TIMESTAMP = "yyyy-MM-dd HH:mm:ss";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_MILLIS = FORMAT_STRING_TIMESTAMP + ".SSS";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_MICROS = FORMAT_STRING_TIMESTAMP + ".SSSSSS";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_NANOS = FORMAT_STRING_TIMESTAMP + ".SSSSSSSSS";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_MILLIS_AND_OFFSET = FORMAT_STRING_TIMESTAMP_WITH_MILLIS + "XXX";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_MICROS_AND_OFFSET = FORMAT_STRING_TIMESTAMP_WITH_MICROS + "XXX";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_NANOS_AND_OFFSET = FORMAT_STRING_TIMESTAMP_WITH_NANOS + "XXX";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_MICROS_AND_OFFSET_NOZ = FORMAT_STRING_TIMESTAMP_WITH_MICROS + "xxx";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_NANOS_AND_OFFSET_NOZ = FORMAT_STRING_TIMESTAMP_WITH_NANOS + "xxx";

	public static final DateTimeFormatter DATE_TIME_FORMATTER_DATE = DateTimeFormatter.ofPattern( FORMAT_STRING_DATE, Locale.ENGLISH );
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIME_WITH_OFFSET = DateTimeFormatter.ofPattern( FORMAT_STRING_TIME_WITH_OFFSET, Locale.ENGLISH );
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIME = DateTimeFormatter.ofPattern( FORMAT_STRING_TIME, Locale.ENGLISH );
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_MILLIS = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_MILLIS,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_MICROS,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_NANOS,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_MILLIS_AND_OFFSET = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_MILLIS_AND_OFFSET,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS_AND_OFFSET = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_MICROS_AND_OFFSET,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS_AND_OFFSET_NOZ = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_MICROS_AND_OFFSET_NOZ,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_NANOS_AND_OFFSET,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET_NOZ = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_NANOS_AND_OFFSET_NOZ,
			Locale.ENGLISH
	);

	public static final String JDBC_ESCAPE_START_DATE = "{d '";
	public static final String JDBC_ESCAPE_START_TIME = "{t '";
	public static final String JDBC_ESCAPE_START_TIMESTAMP = "{ts '";
	public static final String JDBC_ESCAPE_END = "'}";

	/**
	 * Pattern used for parsing literal datetimes in HQL.
	 *
	 * Recognizes timestamps consisting of a date and time separated
	 * by either T or a space, and with an optional offset or time
	 * zone ID. Ideally we should accept both ISO and SQL standard
	 * zoned timestamp formats here.
	 */
	public static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
			.parseCaseInsensitive()
			.append( ISO_LOCAL_DATE )
			.optionalStart().appendLiteral( ' ' ).optionalEnd()
			.optionalStart().appendLiteral( 'T' ).optionalEnd()
			.append( ISO_LOCAL_TIME )
			.optionalStart().appendLiteral( ' ' ).optionalEnd()
			.optionalStart().appendZoneOrOffsetId().optionalEnd()
			.toFormatter();

	private static final ThreadLocal<SimpleDateFormat> LOCAL_DATE_FORMAT =
			ThreadLocal.withInitial( () -> new SimpleDateFormat( FORMAT_STRING_DATE, Locale.ENGLISH ) );
	private static final ThreadLocal<SimpleDateFormat> LOCAL_TIME_FORMAT =
			ThreadLocal.withInitial( () -> new SimpleDateFormat( FORMAT_STRING_TIME, Locale.ENGLISH ) );
	private static final ThreadLocal<SimpleDateFormat> TIME_WITH_OFFSET_FORMAT =
			ThreadLocal.withInitial( () -> new SimpleDateFormat( FORMAT_STRING_TIME_WITH_OFFSET, Locale.ENGLISH ) );
	private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_WITH_MILLIS_FORMAT =
			ThreadLocal.withInitial( () -> new SimpleDateFormat( FORMAT_STRING_TIMESTAMP_WITH_MILLIS, Locale.ENGLISH ) );

	/**
	 * Pattern used for parsing literal offset datetimes in HQL.
	 *
	 * Recognizes timestamps consisting of a date and time separated
	 * by either T or a space, and with a required offset. Ideally we
	 * should accept both ISO and SQL standard timestamp formats here.
	 */
	public static final DateTimeFormatter OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
			.parseCaseInsensitive()
			.append( ISO_LOCAL_DATE )
			.optionalStart().appendLiteral( ' ' ).optionalEnd()
			.optionalStart().appendLiteral( 'T' ).optionalEnd()
			.append( ISO_LOCAL_TIME )
			.optionalStart().appendLiteral( ' ' ).optionalEnd()
			.appendOffset("+HH:mm", "+00")
			.toFormatter();

	public static void appendAsTimestampWithNanos(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone) {
		appendAsTimestamp(
				appender,
				temporalAccessor,
				supportsOffset,
				jdbcTimeZone,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET
		);
	}

	public static void appendAsTimestampWithNanos(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone,
			boolean allowZforZeroOffset) {
		appendAsTimestamp(
				appender,
				temporalAccessor,
				supportsOffset,
				jdbcTimeZone,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS,
				allowZforZeroOffset
						? DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET
						: DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET_NOZ
		);
	}

	public static void appendAsTimestampWithMicros(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone) {
		appendAsTimestamp(
				appender,
				temporalAccessor,
				supportsOffset,
				jdbcTimeZone,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS_AND_OFFSET
		);
	}

	public static void appendAsTimestampWithMicros(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone,
			boolean allowZforZeroOffset) {
		appendAsTimestamp(
				appender,
				temporalAccessor,
				supportsOffset,
				jdbcTimeZone,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS,
				allowZforZeroOffset
						? DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS_AND_OFFSET
						: DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS_AND_OFFSET_NOZ
		);
	}

	public static void appendAsTimestampWithMillis(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone) {
		appendAsTimestamp(
				appender,
				temporalAccessor,
				supportsOffset,
				jdbcTimeZone,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_MILLIS,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_MILLIS_AND_OFFSET
		);
	}

	private static void appendAsTimestamp(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone,
			DateTimeFormatter format,
			DateTimeFormatter formatWithOffset) {
		if ( temporalAccessor.isSupported( ChronoField.OFFSET_SECONDS ) ) {
			if ( supportsOffset ) {
				formatWithOffset.formatTo( temporalAccessor, appender );
			}
			else {
				format.formatTo(
						LocalDateTime.ofInstant(
								Instant.from( temporalAccessor ),
								jdbcTimeZone.toZoneId()
						),
						appender
				);
			}
		}
		else if ( temporalAccessor instanceof Instant ) {
			if ( supportsOffset ) {
				formatWithOffset.formatTo(
						( (Instant) temporalAccessor ).atZone( jdbcTimeZone.toZoneId() ),
						appender
				);
			}
			else {
				format.formatTo(
						LocalDateTime.ofInstant(
								(Instant) temporalAccessor,
								jdbcTimeZone.toZoneId()
						),
						appender
				);
			}
		}
		else {
			format.formatTo( temporalAccessor, appender );
		}
	}

	public static void appendAsDate(SqlAppender appender, TemporalAccessor temporalAccessor) {
		DATE_TIME_FORMATTER_DATE.formatTo( temporalAccessor, appender );
	}

	public static void appendAsTime(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone) {
		if ( temporalAccessor.isSupported( ChronoField.OFFSET_SECONDS ) ) {
			if ( supportsOffset ) {
				DATE_TIME_FORMATTER_TIME_WITH_OFFSET.formatTo( temporalAccessor, appender );
			}
			else {
				DATE_TIME_FORMATTER_TIME.formatTo( LocalTime.from( temporalAccessor ), appender );
			}
		}
		else {
			DATE_TIME_FORMATTER_TIME.formatTo( temporalAccessor, appender );
		}
	}

	public static void appendAsLocalTime(SqlAppender appender, TemporalAccessor temporalAccessor) {
		DATE_TIME_FORMATTER_TIME.formatTo( temporalAccessor, appender );
	}

	public static void appendAsTimestampWithMillis(SqlAppender appender, java.util.Date date, TimeZone jdbcTimeZone) {
		final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( jdbcTimeZone );
			appender.appendSql( simpleDateFormat.format( date ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}

	public static void appendAsTimestampWithMicros(SqlAppender appender, Date date, TimeZone jdbcTimeZone) {
		if ( date instanceof Timestamp ) {
			// java.sql.Timestamp supports nano sec
			appendAsTimestamp(
					appender,
					date.toInstant().atZone( jdbcTimeZone.toZoneId() ),
					false,
					jdbcTimeZone,
					DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS,
					DATE_TIME_FORMATTER_TIMESTAMP_WITH_MICROS_AND_OFFSET
			);
		}
		else {
			// java.util.Date supports only milli sec
			final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
			final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
			try {
				simpleDateFormat.setTimeZone( jdbcTimeZone );
				appender.appendSql( simpleDateFormat.format( date ) );
			}
			finally {
				simpleDateFormat.setTimeZone( originalTimeZone );
			}
		}
	}

	public static void appendAsTimestampWithNanos(SqlAppender appender, Date date, TimeZone jdbcTimeZone) {
		if ( date instanceof Timestamp ) {
			// java.sql.Timestamp supports nano sec
			appendAsTimestamp(
					appender,
					date.toInstant().atZone( jdbcTimeZone.toZoneId() ),
					false,
					jdbcTimeZone,
					DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS,
					DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET
			);
		}
		else {
			// java.util.Date supports only milli sec
			final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
			final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
			try {
				simpleDateFormat.setTimeZone( jdbcTimeZone );
				appender.appendSql( simpleDateFormat.format( date ) );
			}
			finally {
				simpleDateFormat.setTimeZone( originalTimeZone );
			}
		}
	}

	public static void appendAsDate(SqlAppender appender, Date date) {
		appender.appendSql( LOCAL_DATE_FORMAT.get().format( date ) );
	}

	/**
	 * @deprecated Use {@link #appendAsLocalTime(SqlAppender, Date)} instead
	 */
	@Deprecated
	public static void appendAsTime(SqlAppender appender, java.util.Date date) {
		appendAsLocalTime( appender, date );
	}

	public static void appendAsTime(SqlAppender appender, java.util.Date date, TimeZone jdbcTimeZone) {
		final SimpleDateFormat simpleDateFormat = TIME_WITH_OFFSET_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( jdbcTimeZone );
			appender.appendSql( simpleDateFormat.format( date ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}

	public static void appendAsLocalTime(SqlAppender appender, Date date) {
		appender.appendSql( LOCAL_TIME_FORMAT.get().format( date ) );
	}

	public static void appendAsTimestampWithMillis(
			SqlAppender appender,
			java.util.Calendar calendar,
			TimeZone jdbcTimeZone) {
		final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( jdbcTimeZone );
			appender.appendSql( simpleDateFormat.format( calendar.getTime() ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}

	/**
	 * Calendar has no microseconds.
	 *
	 * @deprecated use {@link #appendAsTimestampWithMillis(SqlAppender, Calendar, TimeZone)} instead
	 */
	@Deprecated(forRemoval = true)
	public static void appendAsTimestampWithMicros(SqlAppender appender, Calendar calendar, TimeZone jdbcTimeZone) {
		// it is possible to use micro sec resolution with java.util.Date
		final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( jdbcTimeZone );
			appender.appendSql( simpleDateFormat.format( calendar.getTime() ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}

	public static void appendAsDate(SqlAppender appender, java.util.Calendar calendar) {
		final SimpleDateFormat simpleDateFormat = LOCAL_DATE_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( calendar.getTimeZone() );
			appender.appendSql( simpleDateFormat.format( calendar.getTime() ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}

	/**
	 * @deprecated Use {@link #appendAsLocalTime(SqlAppender, Calendar)} instead
	 */
	@Deprecated
	public static void appendAsTime(SqlAppender appender, java.util.Calendar calendar) {
		appendAsLocalTime( appender, calendar );
	}

	public static void appendAsTime(SqlAppender appender, java.util.Calendar calendar, TimeZone jdbcTimeZone) {
		final SimpleDateFormat simpleDateFormat = TIME_WITH_OFFSET_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( jdbcTimeZone );
			appender.appendSql( simpleDateFormat.format( calendar.getTime() ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}

	public static void appendAsLocalTime(SqlAppender appender, Calendar calendar) {
		appender.appendSql( LOCAL_TIME_FORMAT.get().format( calendar.getTime() ) );
	}

	/**
	 * Do the same conversion that databases do when they encounter a timestamp with a higher precision
	 * than what is supported by a column, which is to round the excess fractions.
	 */
	public static <T extends Temporal> T roundToDefaultPrecision(T temporal, Dialect d) {
		final int defaultTimestampPrecision = d.getDefaultTimestampPrecision();
		if ( defaultTimestampPrecision >= 9 || !temporal.isSupported( ChronoField.NANO_OF_SECOND ) ) {
			return temporal;
		}
		//noinspection unchecked
		return (T) temporal.with(
				ChronoField.NANO_OF_SECOND,
				roundToPrecision( temporal.get( ChronoField.NANO_OF_SECOND ), defaultTimestampPrecision )
		);
	}

	public static long roundToPrecision(int nano, int precision) {
		final int precisionMask = pow10( 9 - precision );
		final int nanosToRound = nano % precisionMask;
		return nano - nanosToRound + ( nanosToRound >= ( precisionMask >> 1 ) ? precisionMask : 0 );
	}

	private static int pow10(int exponent) {
		switch ( exponent ) {
			case 0:
				return 1;
			case 1:
				return 10;
			case 2:
				return 100;
			case 3:
				return 1000;
			case 4:
				return 10000;
			case 5:
				return 100000;
			case 6:
				return 1000000;
			case 7:
				return 10000000;
			case 8:
				return 100000000;
			default:
				return (int) Math.pow( 10, exponent );
		}
	}
}
