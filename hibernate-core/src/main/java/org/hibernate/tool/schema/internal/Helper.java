/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tool.schema.internal;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.internal.Formatter;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.resource.transaction.spi.DdlTransactionIsolator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.schema.extract.internal.DatabaseInformationImpl;
import org.hibernate.tool.schema.extract.spi.DatabaseInformation;
import org.hibernate.tool.schema.internal.exec.AbstractScriptSourceInput;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.hibernate.tool.schema.internal.exec.ScriptSourceInputAggregate;
import org.hibernate.tool.schema.internal.exec.ScriptSourceInputFromFile;
import org.hibernate.tool.schema.internal.exec.ScriptSourceInputFromReader;
import org.hibernate.tool.schema.internal.exec.ScriptSourceInputFromUrl;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToFile;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToUrl;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToWriter;
import org.hibernate.tool.schema.spi.CommandAcceptanceException;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.ScriptSourceInput;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.SqlScriptCommandExtractor;

/**
 * Helper methods.
 *
 * @author Steve Ebersole
 */
public class Helper {

	private static final CoreMessageLogger log = CoreLogging.messageLogger( Helper.class );
	private static final Pattern COMMA_PATTERN = Pattern.compile( "\\s*,\\s*" );

	public static ScriptSourceInput interpretScriptSourceSetting(
			Object scriptSourceSetting, //Reader or String URL
			ClassLoaderService classLoaderService,
			String charsetName ) {
		if ( scriptSourceSetting instanceof Reader ) {
			return new ScriptSourceInputFromReader( (Reader) scriptSourceSetting );
		}
		else {
			final String scriptSourceSettingString = scriptSourceSetting.toString();
			log.debugf( "Attempting to resolve script source setting : %s", scriptSourceSettingString );

			final String[] paths = COMMA_PATTERN.split( scriptSourceSettingString );
			if ( paths.length == 1 ) {
				return interpretScriptSourceSetting( scriptSourceSettingString, classLoaderService, charsetName );
			}
			final AbstractScriptSourceInput[] inputs = new AbstractScriptSourceInput[paths.length];
			for ( int i = 0; i < paths.length; i++ ) {
				inputs[i] = interpretScriptSourceSetting( paths[i], classLoaderService, charsetName ) ;
			}

			return new ScriptSourceInputAggregate( inputs );
		}
	}

	private static AbstractScriptSourceInput interpretScriptSourceSetting(
			String scriptSourceSettingString,
			ClassLoaderService classLoaderService,
			String charsetName) {
		// setting could be either:
		//		1) string URL representation (i.e., "file://...")
		//		2) relative file path (resource lookup)
		//		3) absolute file path

		log.trace( "Trying as URL..." );
		// ClassLoaderService.locateResource() first tries the given resource name as url form...
		final URL url = classLoaderService.locateResource( scriptSourceSettingString );
		if ( url != null ) {
			return new ScriptSourceInputFromUrl( url, charsetName );
		}

		// assume it is a File path
		final File file = new File( scriptSourceSettingString );
		return new ScriptSourceInputFromFile( file, charsetName );
	}

	public static ScriptTargetOutput interpretScriptTargetSetting(
			Object scriptTargetSetting,
			ClassLoaderService classLoaderService,
			String charsetName,
			boolean append) {
		if ( scriptTargetSetting == null ) {
			return null;
		}
		else if ( scriptTargetSetting instanceof Writer ) {
			return new ScriptTargetOutputToWriter( (Writer) scriptTargetSetting );
		}
		else {
			final String scriptTargetSettingString = scriptTargetSetting.toString();
			log.debugf( "Attempting to resolve script source setting : %s", scriptTargetSettingString );

			// setting could be either:
			//		1) string URL representation (i.e., "file://...")
			//		2) relative file path (resource lookup)
			//		3) absolute file path

			log.trace( "Trying as URL..." );
			// ClassLoaderService.locateResource() first tries the given resource name as url form...
			final URL url = classLoaderService.locateResource( scriptTargetSettingString );
			if ( url != null ) {
				return new ScriptTargetOutputToUrl( url, charsetName );
			}

			// assume it is a File path
			final File file = new File( scriptTargetSettingString );
			return new ScriptTargetOutputToFile( file, charsetName, append );
		}
	}

	public static boolean interpretNamespaceHandling(Map<String,Object> configurationValues) {
		//Print a warning if multiple conflicting properties are being set:
		int count = 0;
		if ( configurationValues.containsKey( AvailableSettings.HBM2DDL_CREATE_SCHEMAS ) ) {
			count++;
		}
		if ( configurationValues.containsKey( AvailableSettings.JAKARTA_HBM2DDL_CREATE_SCHEMAS ) ) {
			count++;
		}
		if ( configurationValues.containsKey( AvailableSettings.HBM2DDL_CREATE_NAMESPACES ) ) {
			count++;
		}
		if ( count > 1 ) {
			log.multipleSchemaCreationSettingsDefined();
		}
		// prefer the JPA setting...
		return ConfigurationHelper.getBoolean(
				AvailableSettings.HBM2DDL_CREATE_SCHEMAS,
				configurationValues,
				//Then try the Jakarta JPA setting:
				ConfigurationHelper.getBoolean(
						AvailableSettings.JAKARTA_HBM2DDL_CREATE_SCHEMAS,
						configurationValues,
						//Then try the Hibernate ORM setting:
						ConfigurationHelper.getBoolean(
								AvailableSettings.HBM2DDL_CREATE_NAMESPACES,
								configurationValues,
								false
						)
				)
		);
	}

	public static boolean interpretFormattingEnabled(Map<String,Object> configurationValues) {
		return ConfigurationHelper.getBoolean(
				AvailableSettings.FORMAT_SQL,
				configurationValues,
				false
		);
	}

	public static DatabaseInformation buildDatabaseInformation(
			ServiceRegistry serviceRegistry,
			DdlTransactionIsolator ddlTransactionIsolator,
			SqlStringGenerationContext context,
			SchemaManagementTool tool) {
		final JdbcEnvironment jdbcEnvironment = serviceRegistry.getService( JdbcEnvironment.class );
		try {
			return new DatabaseInformationImpl(
					serviceRegistry,
					jdbcEnvironment,
					context,
					ddlTransactionIsolator,
					tool
			);
		}
		catch (SQLException e) {
			throw jdbcEnvironment.getSqlExceptionHelper().convert( e, "Unable to build DatabaseInformation" );
		}
	}

	public static SqlStringGenerationContext createSqlStringGenerationContext(ExecutionOptions options, Metadata metadata) {
		final Database database = metadata.getDatabase();
		return SqlStringGenerationContextImpl.fromConfigurationMap(
				database.getJdbcEnvironment(),
				database,
				options.getConfigurationValues()
		);
	}

	public static void applySqlStrings(
			String[] sqlStrings,
			Formatter formatter,
			ExecutionOptions options,
			GenerationTarget... targets) {
		if ( sqlStrings == null ) {
			return;
		}

		for ( String sqlString : sqlStrings ) {
			applySqlString( sqlString, formatter, options, targets );
		}
	}

	public static void applySqlString(
			String sqlString,
			Formatter formatter,
			ExecutionOptions options,
			GenerationTarget... targets) {
		if ( StringHelper.isEmpty( sqlString ) ) {
			return;
		}

		String sqlStringFormatted = formatter.format( sqlString );
		for ( GenerationTarget target : targets ) {
			try {
				target.accept( sqlStringFormatted );
			}
			catch (CommandAcceptanceException e) {
				options.getExceptionHandler().handleException( e );
			}
		}
	}

	public static void applyScript(
			ExecutionOptions options,
			SqlScriptCommandExtractor commandExtractor,
			Dialect dialect,
			ScriptSourceInput scriptInput,
			Formatter formatter,
			GenerationTarget[] targets) {
		final List<String> commands = scriptInput.extract(
				reader -> commandExtractor.extractCommands( reader, dialect )
		);
		for ( GenerationTarget target : targets ) {
			target.beforeScript( scriptInput );
		}
		for ( String command : commands ) {
			applySqlString( command, formatter, options, targets );
		}
	}
}
