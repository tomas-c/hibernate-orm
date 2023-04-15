/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

/**
 * Simple version of DatabaseVersion
 */
public class SimpleDatabaseVersion implements DatabaseVersion {
	public static final SimpleDatabaseVersion ZERO_VERSION = new SimpleDatabaseVersion( 0, 0 );

	private final int major;
	private final int minor;
	private final int micro;

	public SimpleDatabaseVersion(DatabaseVersion copySource) {
		this( copySource, true );
	}

	public SimpleDatabaseVersion(DatabaseVersion version, boolean noVersionAsZero) {
		this.major = version.getDatabaseMajorVersion();

		if ( version.getDatabaseMinorVersion() == NO_VERSION ) {
			this.minor = noVersionAsZero ? 0 : NO_VERSION;
		}
		else {
			this.minor = version.getDatabaseMinorVersion();
		}

		if ( version.getDatabaseMicroVersion() == NO_VERSION ) {
			this.micro = noVersionAsZero ? 0 : NO_VERSION;
		}
		else {
			this.micro = version.getDatabaseMicroVersion();
		}
	}

	public SimpleDatabaseVersion(int major, int minor) {
		this( major, minor, 0 );
	}

	public SimpleDatabaseVersion(int major, int minor, int micro) {
		this.major = major;
		this.minor = minor;
		this.micro = micro;
	}

	public SimpleDatabaseVersion(Integer major, Integer minor) {
		if ( major == null ) {
			throw new IllegalArgumentException( "Major version can not be null" );
		}

		this.major = major;
		this.minor = minor == null ? NO_VERSION : minor;
		this.micro = 0;
	}

	@Override
	public int getDatabaseMajorVersion() {
		return major;
	}

	@Override
	public int getDatabaseMinorVersion() {
		return minor;
	}

	@Override
	public int getDatabaseMicroVersion() {
		return micro;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	public int getMicro() {
		return micro;
	}

	@Override
	public String toString() {
		StringBuilder version = new StringBuilder();
		if ( major != NO_VERSION ) {
			version.append( major );
		}
		if ( minor != NO_VERSION ) {
			version.append( "." );
			version.append( minor );
			if ( micro > 0 ) {
				version.append( "." );
				version.append( micro );
			}
		}
		return version.toString();
	}
}
