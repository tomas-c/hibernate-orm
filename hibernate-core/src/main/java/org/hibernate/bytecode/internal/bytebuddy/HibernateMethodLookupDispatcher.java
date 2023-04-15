/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.bytecode.internal.bytebuddy;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hibernate.HibernateException;
import org.hibernate.internal.util.securitymanager.SystemSecurityManager;

/**
 * This dispatcher analyzes the stack frames to detect if a particular call should be authorized.
 * <p>
 * Authorized classes are registered when creating the ByteBuddy proxies.
 * <p>
 * It should only be used when the Security Manager is enabled.
 */
public class HibernateMethodLookupDispatcher {

	/**
	 * The minimum number of stack frames to drop before we can hope to find the caller frame.
	 */
	private static final int MIN_STACK_FRAMES = 3;
	/**
	 * The maximum number of stack frames to explore to find the caller frame.
	 * <p>
	 * Beyond that, we give up and throw an exception.
	 */
	private static final int MAX_STACK_FRAMES = 16;
	private static final PrivilegedAction<Class<?>[]> GET_CALLER_STACK_ACTION;

	// Currently, the bytecode provider is created statically and shared between all the session factories. Thus we
	// can't clear this set when we close a session factory as we might remove elements coming from another one.
	// Considering we can't clear these elements, we use the class names instead of the classes themselves to avoid
	// issues.
	private static Set<String> authorizedClasses = ConcurrentHashMap.newKeySet();

	public static Method getDeclaredMethod(Class<?> type, String name, Class<?>[] parameters) {
		PrivilegedAction<Method> getDeclaredMethodAction = new PrivilegedAction<Method>() {

			@Override
			public Method run() {
				try {
					return type.getDeclaredMethod( name, parameters );
				}
				catch (NoSuchMethodException | SecurityException e) {
					return null;
				}
			}
		};

		return doPrivilegedAction( getDeclaredMethodAction );
	}

	public static Method getMethod(Class<?> type, String name, Class<?>[] parameters) {
		PrivilegedAction<Method> getMethodAction = new PrivilegedAction<Method>() {

			@Override
			public Method run() {
				try {
					return type.getMethod( name, parameters );
				}
				catch (NoSuchMethodException | SecurityException e) {
					return null;
				}
			}
		};

		return doPrivilegedAction( getMethodAction );
	}

	private static Method doPrivilegedAction(PrivilegedAction<Method> privilegedAction) {
		Class<?> callerClass = getCallerClass();

		if ( !authorizedClasses.contains( callerClass.getName() ) ) {
			throw new SecurityException( "Unauthorized call by class " + callerClass );
		}

		return SystemSecurityManager.isSecurityManagerEnabled() ? AccessController.doPrivileged( privilegedAction ) :
			privilegedAction.run();
	}

	static void registerAuthorizedClass(String className) {
		authorizedClasses.add( className );
	}

	static {
		// The action below will return the action used at runtime to retrieve the caller stack
		PrivilegedAction<PrivilegedAction<Class<?>[]>> initializeGetCallerStackAction = new PrivilegedAction<PrivilegedAction<Class<?>[]>>() {
			@Override
			public PrivilegedAction<Class<?>[]> run() {
				try {
					return new StackWalkerGetCallerStackAction(StackWalker.getInstance( StackWalker.Option.RETAIN_CLASS_REFERENCE) );
				}
				catch (Throwable e) {
					throw new HibernateException( "Unable to initialize the stack walker", e );
				}
			}
		};

		GET_CALLER_STACK_ACTION = SystemSecurityManager.isSecurityManagerEnabled()
				? AccessController.doPrivileged( initializeGetCallerStackAction )
				: initializeGetCallerStackAction.run();
	}

	private static Class<?> getCallerClass() {
		Class<?>[] stackTrace = SystemSecurityManager.isSecurityManagerEnabled()
				? AccessController.doPrivileged( GET_CALLER_STACK_ACTION )
				: GET_CALLER_STACK_ACTION.run();

		// this shouldn't happen but let's be safe
		if ( stackTrace.length <= MIN_STACK_FRAMES ) {
			throw new SecurityException( "Unable to determine the caller class" );
		}

		boolean hibernateMethodLookupDispatcherDetected = false;
		// start at the 4th frame and limit that to the MAX_STACK_FRAMES first frames
		int maxFrames = Math.min( MAX_STACK_FRAMES, stackTrace.length );
		for ( int i = MIN_STACK_FRAMES; i < maxFrames; i++ ) {
			if ( stackTrace[i].getName().equals( HibernateMethodLookupDispatcher.class.getName() ) ) {
				hibernateMethodLookupDispatcherDetected = true;
				continue;
			}
			if ( hibernateMethodLookupDispatcherDetected ) {
				return stackTrace[i];
			}
		}

		throw new SecurityException( "Unable to determine the caller class" );
	}

	/**
	 * A privileged action that retrieves the caller stack using a stack walker.
	 */
	private static class StackWalkerGetCallerStackAction implements PrivilegedAction<Class<?>[]> {
		private final StackWalker stackWalker;

		StackWalkerGetCallerStackAction(StackWalker stackWalker) {
			this.stackWalker = stackWalker;
		}

		@Override
		public Class<?>[] run() {
			try {
				return stackWalker.walk( stackFrameExtractFunction );
			}
			catch (RuntimeException e) {
				throw new SecurityException( "Unable to determine the caller class", e );
			}
		}

		private final Function<Stream<StackWalker.StackFrame>, Class<?>[]> stackFrameExtractFunction = new Function<>() {
			@Override
			public Class<?>[] apply(Stream<StackWalker.StackFrame> stream) {
				return stream.map( stackFrameGetDeclaringClassFunction )
						.limit( MAX_STACK_FRAMES )
						.toArray( Class<?>[]::new );
			}
		};

		private final Function<StackWalker.StackFrame, Class<?>> stackFrameGetDeclaringClassFunction = new Function<>() {
			@Override
			public Class<?> apply(StackWalker.StackFrame frame) {
				try {
					return frame.getDeclaringClass();
				}
				catch (RuntimeException e) {
					throw new HibernateException( "Unable to get stack frame declaring class", e );
				}
			}
		};
	}
}
