/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.core.history;

import java.net.URI;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.history.provider.FileRevision;


/**
 * Represents an individual revision of a file.
 * 
 * <p>
 * This interface is not intended to be implemented by clients. Clients can
 * instead subclass {@link FileRevision}.
 * 
 * <p> <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @since 3.2
 */
public interface IFileRevision extends IFileState {

	/**
	 * Returns the storage for this file revision.
	 * If the returned storage is an instance of
	 * <code>IFile</code> clients can assume that this
	 * file state represents the current state of
	 * the returned <code>IFile</code>.
	 * @return IStorage containing file storage 
	 */
	public IStorage getStorage(IProgressMonitor monitor) throws CoreException;

	/**
	 * Returns the name of the file to which this state is associated
	 * @return String containing the name of the file
	 */
	public String getName();

	/**
	 * Returns the URI of the file to which this state is associated
	 * or <code>null</code> if the file does not have a URI.
	 * @return URI of the file to which this state is associated
	 */
	public URI getURI();

	/**
	 * Returns the time stamp of this revision as a long or <code>-1</code>
	 * if the timestamp is unknown.
	 * 
	 * @return a long that represents the time of this revision as the number of milliseconds
	 * since the base time
	 *
	 * @see java.lang.System#currentTimeMillis()
	 */
	public long getTimestamp();

	/**
	 * Returns whether the file represented by this state exists.
	 * @return whether the file represented by this state exists
	 */
	public boolean exists();
	
	/**
	 * Returns the <em>unique</em> identifier for this file revision 
	 * or <code>null</code> if one is not available. If <code>null</code>
	 * is returned, clients can use the timestamp to differentiate 
	 * revisions.
	 * @return the <em>unique</em> identifier for this file revision 
	 * or <code>null</code>
	 */
	public abstract String getContentIdentifier();

	/**
	 *  Returns the author of this revision or <code>null</code> if
	 *  this information is not available.
	 *  
	 *  @return the author of this revision or <code>null</code>
	 */
	public abstract String getAuthor();

	/**
	 * Returns the comment for this file revision or <code>null</code> if
	 * this information is not available.
	 * 
	 * @return the comment for this file revision or <code>null</code>
	 */
	public abstract String getComment();

	/**
	 * Returns the set of tags available for this file revision.
	 * 
	 * @return an array of ITag's if ITags exist for this revision or an empty ITag array
	 * if no tags exist
	 */
	public abstract ITag[] getTags();
}
