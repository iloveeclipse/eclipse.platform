package org.eclipse.debug.core.model;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.debug.core.DebugException;

/**
 * Provides the ability to suspend and resume a thread
 * or debug target.
 * <p>
 * Clients may implement this interface.
 * </p>
 * <p>
 * <b>Note:</b> This class/interface is part of an interim API that is still under development and expected to 
 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback 
 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
 * (repeatedly) as the API evolves.
 * </p>
 */
public interface ISuspendResume {
	/**
	 * Returns whether this element can currently be resumed.
	 *
	 * @return whether this element can currently be resumed
	 */
	public boolean canResume();
	/**
	 * Returns whether this element can currently be suspended.
	 *
	 * @return whether this element can currently be suspended
	 */
	public boolean canSuspend();
	/**
	 * Returns whether this element is currently suspened.
	 *
	 * @return whether this element is currently suspened
	 */
	public boolean isSuspended();
	/**
	 * Causes this element to resume its execution. Has no effect
	 * on an element that is not suspended. This call is non-blocking.
	 *
	 * @exception DebugException on failure. Reasons include:<ul>
	 * <li>TARGET_REQUEST_FAILED - The request failed in the target
	 * <li>NOT_SUPPORTED - The capability is not supported by the target
	 * </ul>
	 */
	public void resume() throws DebugException;
	/**
	 * Causes this element to suspend its execution.
	 * Has no effect on an already suspened element.
	 * Implementations may be blocking or non-blocking.
	 *
	 * @exception DebugException on failure. Reasons include:<ul>
	 * <li>TARGET_REQUEST_FAILED - The request failed in the target
	 * <li>NOT_SUPPORTED - The capability is not supported by the target
	 * </ul>
	 */
	public void suspend() throws DebugException;
}
