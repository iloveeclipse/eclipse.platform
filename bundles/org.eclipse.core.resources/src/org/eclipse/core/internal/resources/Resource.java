package org.eclipse.core.internal.resources;

/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 2000
 */
import org.eclipse.core.runtime.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.internal.localstore.*;
import org.eclipse.core.internal.properties.PropertyManager;
import org.eclipse.core.internal.utils.*;
import org.eclipse.core.internal.watson.*;
import java.io.*;
import java.util.*;

public abstract class Resource extends PlatformObject implements IResource, ICoreConstants, Cloneable {
	/* package */ IPath path;
	/* package */ Workspace workspace;
protected Resource(IPath path, Workspace workspace) {
	this.path = path.removeTrailingSeparator();
	this.workspace = workspace;
}
/**
 * @see IResource#accept(IResourceVisitor)
 */
public void accept(IResourceVisitor visitor) throws CoreException {
	accept(visitor, IResource.DEPTH_INFINITE, false);
}
/**
 * @see IResource#accept(IResourceVisitor, int, boolean)
 */
public void accept(IResourceVisitor visitor, int depth, boolean includePhantoms) throws CoreException {
	ResourceInfo info = getResourceInfo(includePhantoms, false);
	checkExists(getFlags(info), true);
	if (!visitor.visit(this) || depth == DEPTH_ZERO)
		return;
	// get the info again because it might have been changed by the visitor
	info = getResourceInfo(includePhantoms, false);
	if (info == null)
		return;
	// thread safety: (cache the type to avoid changes -- we might not be inside an operation)
	int type = info.getType();
	if (type == FILE)
		return;
	if (depth == DEPTH_ONE)
		depth = DEPTH_ZERO;
	// if we had a gender change we need to fix up the resource before asking for its members
	IContainer resource = getType() != type ? (IContainer) workspace.newResource(getFullPath(), type) : (IContainer) this;
	IResource[] members = resource.members(includePhantoms);
	for (int i = 0; i < members.length; i++)
		members[i].accept(visitor, depth, includePhantoms);
}
protected void assertCopyRequirements(IPath destination, int destinationType) throws CoreException {
	IStatus status = checkCopyRequirements(destination, destinationType);
	if (!status.isOK()) {
		// this assert is ok because the error cases generated by the
		// check method above indicate assertion conditions.
		Assert.isTrue(false, status.getChildren()[0].getMessage());
	}
}
protected void assertMoveRequirements(IPath destination, int destinationType) throws CoreException {
	IStatus status = checkMoveRequirements(destination, destinationType);
	if (!status.isOK()) {
		// this assert is ok because the error cases generated by the
		// check method above indicate assertion conditions.
		Assert.isTrue(false, status.getChildren()[0].getMessage());
	}
}
public void checkAccessible(int flags) throws CoreException {
	checkExists(flags, true);
}
/**
 * This method reports errors in two different ways. It can throw a
 * CoreException or log a status. CoreExceptions are used according
 * to the specification of the copy method. Programming errors, that
 * would usually be prevented by using an "Assert" code, are reported as
 * an IStatus.
 * We're doing this way because we have two different methods to copy
 * resources: IResource#copy and IWorkspace#copy. The first one gets
 * the error and throws its message in an AssertionFailureException. The
 * second one just throws a CoreException using the status returned
 * by this method.
 * 
 * @see IResource#copy
 */
public IStatus checkCopyRequirements(IPath destination, int destinationType) throws CoreException {
	MultiStatus status = new MultiStatus(ResourcesPlugin.PI_RESOURCES, IResourceStatus.INVALID_VALUE, Policy.bind("copyNotMet", null), null);
	if (destination == null)
		return new ResourceStatus(IResourceStatus.INVALID_VALUE, getFullPath(), Policy.bind("destNotNull", null));
	destination = makePathAbsolute(destination);
	if (getFullPath().isPrefixOf(destination))
		status.add(new ResourceStatus(IResourceStatus.INVALID_VALUE, getFullPath(), Policy.bind("destNotSub", null)));
	checkValidPath(destination, destinationType);

	ResourceInfo info = getResourceInfo(false, false);
	int flags = getFlags(info);
	checkAccessible(flags);
	checkLocal(flags, DEPTH_INFINITE);

	Resource dest = (Resource) workspace.newResource(destination, destinationType);
	info = dest.getResourceInfo(false, false);
	dest.checkDoesNotExist(getFlags(info), false);

	// ensure we aren't trying to copy a file to a project
	if (getType() == IResource.FILE && dest.getType() == IResource.PROJECT)
		throw new ResourceException(new ResourceStatus(IResourceStatus.INVALID_VALUE, getFullPath(), "Cannot copy a file to a project."));

	// we can't copy into a closed project
	if (destinationType != IResource.PROJECT) {
		Project project = (Project) dest.getProject();
		info = project.getResourceInfo(false, false);
		project.checkAccessible(getFlags(info));

		Container parent = (Container) dest.getParent();
		if (!parent.equals(project)) {
			info = parent.getResourceInfo(false, false);
			parent.checkExists(getFlags(info), true);
		}
	}

	return status.isOK() ? (IStatus) new ResourceStatus(IResourceStatus.OK, Policy.bind("copyMet", null)) : (IStatus) status;
}
/**
 * Checks that this resource does not exist.  
 *
 * @exception CoreException if this resource exists
 */
public void checkDoesNotExist(int flags, boolean checkType) throws CoreException {
	// See if there is any resource at all.  If none then we are happy.
	if (!exists(flags, false))
		return;
	// We know there is something in the tree at this path.
	// If we are checking type then go ahead and check the type.
	// If there is nothing there of this resource's type, then return.
	if ((checkType && !exists(flags, checkType)))
		return;
	String message = Policy.bind("mustNotExist", new String[] { getFullPath().toString()});
	throw new ResourceException(checkType ? IResourceStatus.RESOURCE_EXISTS : IResourceStatus.PATH_OCCUPIED, getFullPath(), message, null);
}
/**
 * Checks that this resource exists.
 * If checkType is true, the type of this resource and the one in the tree must match.
 *
 * @exception CoreException if this resource does not exist
 */
public void checkExists(int flags, boolean checkType) throws CoreException {
	if (!exists(flags, checkType)) {
		String message = Policy.bind("mustExist", new String[] { getFullPath().toString()});
		throw new ResourceException(IResourceStatus.RESOURCE_NOT_FOUND, getFullPath(), message, null);
	}
}
/**
 * Checks that this resource is local to the given depth.  
 *
 * @exception CoreException if this resource is not local
 */
public void checkLocal(int flags, int depth) throws CoreException {
	if (!isLocal(flags, depth)) {
		String message = Policy.bind("mustBeLocal", new String[] { getFullPath().toString()});
		throw new ResourceException(IResourceStatus.RESOURCE_NOT_LOCAL, getFullPath(), message, null);
	}
}
/**
 * This method reports errors in two different ways. It can throw a
 * CoreException or log a status. CoreExceptions are used according
 * to the specification of the move method. Programming errors, that
 * would usually be prevented by using an "Assert" code, are reported as
 * an IStatus.
 * We're doing this way because we have two different methods to move
 * resources: IResource#move and IWorkspace#move. The first one gets
 * the error and throws its message in an AssertionFailureException. The
 * second one just throws a CoreException using the status returned
 * by this method.
 * 
 * @see IResource#move
 */
protected IStatus checkMoveRequirements(IPath destination, int destinationType) throws CoreException {
	MultiStatus status = new MultiStatus(ResourcesPlugin.PI_RESOURCES, IResourceStatus.INVALID_VALUE, Policy.bind("moveNotMet", null), null);
	if (destination == null)
		return new ResourceStatus(IResourceStatus.INVALID_VALUE, getFullPath(), Policy.bind("destNotNull", null));
	destination = makePathAbsolute(destination);
	if (getFullPath().isPrefixOf(destination))
		status.add(new ResourceStatus(IResourceStatus.INVALID_VALUE, getFullPath(), Policy.bind("destNotSub", null)));
	checkValidPath(destination, destinationType);

	ResourceInfo info = getResourceInfo(false, false);
	int flags = getFlags(info);
	checkAccessible(flags);
	checkLocal(flags, DEPTH_INFINITE);

	Resource dest = (Resource) workspace.newResource(destination, destinationType);
	info = dest.getResourceInfo(false, false);
	dest.checkDoesNotExist(getFlags(info), false);

	// ensure we aren't trying to move a file to a project
	if (getType() == IResource.FILE && dest.getType() == IResource.PROJECT)
		throw new ResourceException(new ResourceStatus(IResourceStatus.INVALID_VALUE, getFullPath(), "Cannot move a file to a project."));

	// we can't move into a closed project
	if (destinationType != IResource.PROJECT) {
		Project project = (Project) dest.getProject();
		info = project.getResourceInfo(false, false);
		project.checkAccessible(getFlags(info));

		Container parent = (Container) dest.getParent();
		if (!parent.equals(project)) {
			info = parent.getResourceInfo(false, false);
			parent.checkExists(getFlags(info), true);
		}
	}
	return status.isOK() ? (IStatus) new ResourceStatus(IResourceStatus.OK, Policy.bind("moveMet", null)) : (IStatus) status;
}
/**
 * Checks that the supplied path is valid according to Workspace.validatePath().
 *
 * @exception CoreException if the path is not valid
 */
public void checkValidPath(IPath path, int type) throws CoreException {
	IStatus result = workspace.validatePath(path.toString(), type);
	if (!result.isOK())
		throw new ResourceException(result);
}
/**
 * @see IResource
 */
public void clearHistory(IProgressMonitor monitor) throws CoreException {
	getLocalManager().getHistoryStore().removeAll(this);
}
public void convertToPhantom() throws CoreException {
	ResourceInfo info = getResourceInfo(false, true);
	if (info == null || isPhantom(getFlags(info)))
		return;
	info.clearSessionProperties();
	info.set(M_PHANTOM);
	getLocalManager().updateLocalSync(info, I_NULL_SYNC_INFO, getType() == FILE);
	info.setModificationStamp(IResource.NULL_STAMP);
	// should already be done by the #deleteResource call but left in 
	// just to be safe and for code clarity.
	info.setMarkers(null);
}
/*
 * Used when a folder is to be copied to a project.
 * @see IResource#copy
 */
public void copy(IProjectDescription destDesc, boolean force, IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask(Policy.bind("copying", new String[] { getFullPath().toString()}), Policy.totalWork);
		try {
			workspace.prepareOperation();
			// The following assert method throws CoreExceptions as stated in the IResource.copy API
			// and assert for programming errors. See checkCopyRequirements for more information.
			IPath destPath = new Path(destDesc.getName()).makeAbsolute();
			assertCopyRequirements(destPath, IResource.PROJECT);
			Project destProject = (Project) workspace.getRoot().getProject(destPath.lastSegment());
			workspace.beginOperation(true);

			// create and open the new project
			destProject.create(destDesc, Policy.subMonitorFor(monitor, Policy.opWork * 5 / 100));
			destProject.open(Policy.subMonitorFor(monitor, Policy.opWork * 5 / 100));

			// copy the children
			// FIXME: fix the progress monitor here...create a sub monitor and do a worked(1) after each child instead
			IResource[] children = ((IContainer) this).members();
			for (int i = 0; i < children.length; i++) {
				Resource child = (Resource) children[i];
				child.copy(destPath.append(child.getName()), force, Policy.subMonitorFor(monitor, Policy.opWork * 60 / 100 / children.length));
			}

			// copy over the properties
			getPropertyManager().copy(this, destProject, DEPTH_ZERO);
			monitor.worked(Policy.opWork * 15 / 100);

		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
/**
 * @see IResource#copy
 */
public void copy(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException {
	if (destination.isAbsolute() && destination.segmentCount() == 1) {
		copy(workspace.newProjectDescription(destination.lastSegment()), force, monitor);
		return;
	}
	try {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(Policy.bind("copying", new String[] { getFullPath().toString()}), Policy.totalWork);
		try {
			workspace.prepareOperation();
			// The following assert method throws CoreExceptions as stated in the IResource.copy API
			// and assert for programming errors. See checkCopyRequirements for more information.
			assertCopyRequirements(destination, getType());

			workspace.beginOperation(true);
			Resource destResource = workspace.newResource(makePathAbsolute(destination), getType());
			getLocalManager().copy(this, destResource, force, Policy.subMonitorFor(monitor, Policy.opWork));
		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
/**
 * Count the number of resources in the tree from this container to the
 * specified depth. Include this resource. Include phantoms if
 * the phantom boolean is true.
 */
public int countResources(int depth, boolean phantom) throws CoreException {
	ResourceInfo info = getResourceInfo(phantom, false);
	if (!exists(getFlags(info), false))
		return 0;
	int total = 1;
	if (getType() == FILE || depth == DEPTH_ZERO)
		return total;
	if (depth == DEPTH_ONE)
		depth = DEPTH_ZERO;
	IResource[] children = ((Container) this).members(phantom);
	for (int i = 0; i < children.length; i++) {
		Resource child = (Resource) children[i];
		total += child.countResources(depth, phantom);
	}
	return total;
}
/**
 * @see IResource
 */
public IMarker createMarker(String type) throws CoreException {
	Assert.isNotNull(type);
	try {
		workspace.prepareOperation();
		ResourceInfo resourceInfo = getResourceInfo(false, false);
		checkAccessible(getFlags(resourceInfo));

		workspace.beginOperation(true);
		MarkerInfo info = new MarkerInfo();
		info.setType(type);
		workspace.getMarkerManager().add(this, new MarkerInfo[] { info });
		return new Marker(this, info.getId());
	} finally {
		workspace.endOperation(false, null);
	}
}
/**
 * @see IResource
 */
public void delete(boolean force, IProgressMonitor monitor) throws CoreException {
	delete(force, false, monitor);
}
/**
 * @see IResource
 */
public void delete(boolean force, boolean keepHistory, IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		String title = Policy.bind("deleting", new String[] { getFullPath().toString()});
		monitor.beginTask(title, Policy.totalWork);
		try {
			workspace.prepareOperation();
			/* if there is no such resource (including type check) then there is nothing
			   to delete so just return. */
			if (!exists())
				return;

			workspace.beginOperation(true);
			getLocalManager().delete(this, force, true, keepHistory, Policy.subMonitorFor(monitor, Policy.opWork));
		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
/**
 * @see IResource
 */
public synchronized void deleteMarkers(String type, boolean includeSubtypes, int depth) throws CoreException {
	try {
		workspace.prepareOperation();
		ResourceInfo info = getResourceInfo(false, false);
		checkAccessible(getFlags(info));

		workspace.beginOperation(true);
		workspace.getMarkerManager().removeMarkers(this, type, includeSubtypes, depth);
	} finally {
		workspace.endOperation(false, null);
	}
}
/**
 * This method should be called to delete a resource from the tree because it will also
 * delete its properties and markers.
 */
public void deleteResource(boolean convertToPhantom, MultiStatus status) throws CoreException {
	/* delete properties */
	try {
		getPropertyManager().deleteProperties(this);
	} catch (CoreException e) {
		status.add(e.getStatus());
	}

	/* remove markers on this resource and its descendents. */
	if (exists())
		getMarkerManager().removeMarkers(this);

	/* if we are synchronizing, do not delete the resource. Convert it
	   into a phantom. Actual deletion will happen when we refresh or push. */
	if (convertToPhantom && getType() != PROJECT && synchronizing(getResourceInfo(true, false)))
		convertToPhantom();
	else
		workspace.deleteResource(this);
}
/**
 * @see IResource#equals
 */
public boolean equals(Object target) {
	if (this == target)
		return true;
	if (!(target instanceof Resource))
		return false;
	Resource resource = (Resource) target;
	return getFullPath().equals(resource.getFullPath()) && workspace.equals(resource.getWorkspace()) && getType() == resource.getType();
}
/**
 * @see IResource#exists
 */
public boolean exists() {
	ResourceInfo info = getResourceInfo(false, false);
	return exists(getFlags(info), true);
}
public boolean exists(int flags, boolean checkType) {
	return flags != NULL_FLAG && !(checkType && ResourceInfo.getType(flags) != getType());
}
/**
 * @see IResource#findMarker
 */
public IMarker findMarker(long id) throws CoreException {
	return workspace.getMarkerManager().findMarker(this, id);
}
/**
 * @see IResource#findMarkers
 */
public synchronized IMarker[] findMarkers(String type, boolean includeSubtypes, int depth) throws CoreException {
	// FIXME: since this method is not an operation, this pre-condition check might change 
	// while the visitor inside getMarkerManager().findMarkers() runs. Should change it.
	ResourceInfo info = getResourceInfo(false, false);
	checkAccessible(getFlags(info));
	return workspace.getMarkerManager().findMarkers(this, type, includeSubtypes, depth);
}
protected void fixupAfterMoveSource() throws CoreException {
	ResourceInfo info = getResourceInfo(true, true);
	if (!synchronizing(info)) {
		workspace.deleteResource(this);
		return;
	}
	info.clearSessionProperties();
	info.clear(M_LOCAL_EXISTS);
	info.setLocalSyncInfo(I_NULL_SYNC_INFO);
	info.set(M_PHANTOM);
	info.setModificationStamp(IResource.NULL_STAMP);
	info.setMarkers(null);
}
/**
 * @see IResource#getFileExtension
 */
public String getFileExtension() {
	String name = getName();
	int index = name.lastIndexOf('.');
	if (index == -1)
		return null;
	if (index == (name.length() - 1))
		return "";
	return name.substring(index + 1);
}
public int getFlags(ResourceInfo info) {
	return (info == null) ? NULL_FLAG : info.getFlags();
}
/**
 * @see IResource#getFullPath
 */
public IPath getFullPath() {
	return path;
}
public FileSystemResourceManager getLocalManager() {
	return workspace.getFileSystemManager();
}
/**
 * @see IResource#getLocation
 */
public IPath getLocation() {
	IProject project = getProject();
	if (project != null && !project.exists())
		return null;
	return getLocalManager().locationFor(this);
}
/**
 * @see IResource
 */
public IMarker getMarker(long id) {
	return new Marker(this, id);
}
protected MarkerManager getMarkerManager() {
	return workspace.getMarkerManager();
}
/**
 * @see IResource
 */
public long getModificationStamp() {
	ResourceInfo info = getResourceInfo(false, false);
	return info == null ? IResource.NULL_STAMP : info.getModificationStamp();
}
/**
 * @see IResource#getName
 */
public String getName() {
	return path.lastSegment();
}
/**
 * @see IResource#getParent
 */
public IContainer getParent() {
	IPath parent = path.removeLastSegments(1);
	if (parent.isRoot() || parent.isEmpty())
		return null;
	if (parent.segmentCount() == 1)
		return workspace.getRoot().getProject(parent.lastSegment());
	else
		return workspace.getRoot().getFolder(parent);
}
/**
 * @see IResource
 */
public String getPersistentProperty(QualifiedName key) throws CoreException {
	ResourceInfo info = getResourceInfo(false, false);
	int flags = getFlags(info);
	checkAccessible(flags);
	checkLocal(flags, DEPTH_ZERO);
	return getPropertyManager().getProperty(this, key);
}
/**
 * @see IResource#getProject
 */
public IProject getProject() {
	return workspace.getRoot().getProject(path.segment(0));
}
/**
 * @see IResource#getProjectRelativePath
 */
public IPath getProjectRelativePath() {
	return getFullPath().removeFirstSegments(ICoreConstants.PROJECT_SEGMENT_LENGTH);
}
public PropertyManager getPropertyManager() {
	return workspace.getPropertyManager();
}
/**
 * Returns the resource info.  This resource must exist.
 * If the phantom flag is true, phantom resources are considered.
 * If the mutable flag is true, a mutable info is returned.
 */
public ResourceInfo getResourceInfo(boolean phantom, boolean mutable) {
	return workspace.getResourceInfo(getFullPath(), phantom, mutable);
}
/**
 * @see IResource
 */
public Object getSessionProperty(QualifiedName key) throws CoreException {
	ResourceInfo info = getResourceInfo(false, false);
	int flags = getFlags(info);
	checkAccessible(flags);
	checkLocal(flags, DEPTH_ZERO);
	return info.getSessionProperty(key);
}
/**
 * @see IResource#getType
 */
public abstract int getType();
public String getTypeString() {
	switch (getType()) {
		case FILE :
			return "L";
		case FOLDER :
			return "F";
		case PROJECT :
			return "P";
		case ROOT:
			return "R";
	}
	return "";
}
/**
 * @see IResource#getWorkspace
 */
public IWorkspace getWorkspace() {
	return workspace;
}
public int hashCode() {
	// the container may be null if the identified resource 
	// does not exist so don't bother with it in the hash
	return getFullPath().hashCode();
}
/**
 * Sets the M_LOCAL_EXISTS flag. Is internal so we don't have
 * to begin an operation.
 */
protected void internalSetLocal(boolean flag, int depth) throws CoreException {
	ResourceInfo info = getResourceInfo(true, true);
	if (flag && !isPhantom(getFlags(info))) {
		info.set(M_LOCAL_EXISTS);
		workspace.updateModificationStamp(info);
	} else {
		info.clear(M_LOCAL_EXISTS);
		info.setModificationStamp(IResource.NULL_STAMP);
	}
	if (getType() == IResource.FILE || depth == IResource.DEPTH_ZERO)
		return;
	if (depth == IResource.DEPTH_ONE)
		depth = IResource.DEPTH_ZERO;
	IResource[] children = ((IContainer) this).members();
	for (int i = 0; i < children.length; i++)
		 ((Resource) children[i]).internalSetLocal(flag, depth);
}
/**
 * @see IResource
 */
public boolean isAccessible() {
	return exists();
}
/**
 * @see IResource
 */
public boolean isLocal(int depth) {
	ResourceInfo info = getResourceInfo(false, false);
	return isLocal(getFlags(info), depth);
}
public boolean isLocal(int flags, int depth) {
	if (getType() == PROJECT)
		return flags != NULL_FLAG; // exists
	else
		return flags != NULL_FLAG && ResourceInfo.isSet(flags, M_LOCAL_EXISTS);
}
/**
 * @see IResource
 */
public boolean isPhantom() {
	ResourceInfo info = getResourceInfo(true, false);
	return isPhantom(getFlags(info));
}
public boolean isPhantom(int flags) {
	return flags != NULL_FLAG && ResourceInfo.isSet(flags, M_PHANTOM);
}
/**
 * @see IResource
 */
public boolean isReadOnly() {
	return CoreFileSystemLibrary.isReadOnly(getLocation().toOSString());
}
protected IPath makePathAbsolute(IPath target) {
	if (target.isAbsolute())
		return target;
	return getParent().getFullPath().append(target);
}
/*
 * Used when a folder is to be moved to a project.
 * @see IResource#move
 */
public void move(IProjectDescription destDesc, boolean force, boolean keepHistory, IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask(Policy.bind("moving", new String[] { getFullPath().toString()}), Policy.totalWork);
		try {
			workspace.prepareOperation();
			// The following assert method throws CoreExceptions as stated in the IResource.move API
			// and assert for programming errors. See checkCopyRequirements for more information.
			IPath destPath = Path.ROOT.append(destDesc.getName());
			assertMoveRequirements(destPath, IResource.PROJECT);
			// copy the original properties to the new location and then move the resources
			// on disk.  If the move fails then delete the newly copied properties.  Otherwise, 
			// move the resources in the tree and delete the original (already copied) properties.
			Project destProject = (Project) workspace.getRoot().getProject(destDesc.getName());

			workspace.beginOperation(true);

			// create and open the new project and reset the node id so it looks like a move rather
			// than a create.
			destProject.create(destDesc, Policy.subMonitorFor(monitor, Policy.opWork * 5 / 100));
			destProject.open(Policy.subMonitorFor(monitor, Policy.opWork * 5 / 100));
			destProject.getResourceInfo(false, true).setNodeId(getResourceInfo(false, false).getNodeId());

			// move the children
			// FIXME: fix the progress monitor here...create a sub monitor and do a worked(1) after each child instead
			IResource[] children = ((IContainer) this).members();
			for (int i = 0; i < children.length; i++) {
				Resource child = (Resource) children[i];
				child.move(destPath.append(child.getName()), force, keepHistory, Policy.subMonitorFor(monitor, Policy.opWork * 40 / 100 / children.length));
			}

			// copy over the properties
			getPropertyManager().copy(this, destProject, DEPTH_ZERO);
			monitor.worked(Policy.opWork * 10 / 100);

			// generate the appropriate marker deltas
			getMarkerManager().moved(this, destProject, IResource.DEPTH_INFINITE);
			monitor.worked(Policy.opWork * 10 / 100);

			// delete the source
			delete(force, keepHistory, Policy.subMonitorFor(monitor, Policy.opWork * 30 / 100));

		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
/**
 * @see IResource#move
 */
public void move(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException {
	move(destination, force, false, monitor);
}
/**
 * @see IResource#move
 */
public void move(IPath destination, boolean force, boolean keepHistory, IProgressMonitor monitor) throws CoreException {
	if (destination.isAbsolute() && destination.segmentCount() == 1) {
		move(workspace.newProjectDescription(destination.lastSegment()), force, keepHistory, monitor);
		return;
	}
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask(Policy.bind("moving", new String[] { getFullPath().toString()}), Policy.totalWork);
		try {
			workspace.prepareOperation();
			// The following assert method throws CoreExceptions as stated in the IResource.move API
			// and assert for programming errors. See checkCopyRequirements for more information.
			assertMoveRequirements(destination, getType());
			destination = makePathAbsolute(destination);
			// copy the original properties to the new location and then move the resources
			// on disk.  If the move fails then delete the newly copied properties.  Otherwise, 
			// move the resources in the tree and delete the original (already copied) properties.
			Resource dest = (Resource) workspace.newResource(destination, getType());

			workspace.beginOperation(true);
			boolean success = false;
			try {
				getPropertyManager().copy(this, dest, DEPTH_INFINITE);
				monitor.worked(Policy.opWork * 20 / 100);
				moveInFileSystem(destination, force, keepHistory, Policy.subMonitorFor(monitor, Policy.opWork * 20 / 100));
				success = true;
			} finally {
				if (!success) {
					// if we got here some exception was thrown
					try {
						getPropertyManager().deleteProperties(dest);
					} catch (CoreException ex) {
						// we don't want to throw this CoreException
					}
					// and the first exception is thrown from here
				}
			}
			getPropertyManager().deleteProperties(this);
			monitor.worked(Policy.opWork * 20 / 100);
			workspace.move(this, destination);
			getMarkerManager().moved(this, dest, IResource.DEPTH_INFINITE);
			monitor.worked(Policy.opWork * 20 / 100);
			getLocalManager().refresh(this, DEPTH_INFINITE, Policy.subMonitorFor(monitor, Policy.opWork * 20 / 100));
		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
protected void moveInFileSystem(IPath destination, boolean force, boolean keepHistory, IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask(Policy.bind("moving", new String[] { getFullPath().toString()}), 100);
		RefreshLocalWithStatusVisitor visitor = new RefreshLocalWithStatusVisitor(Policy.bind("moveProblem", null), Policy.bind("resourcesDifferent", null), monitor);
		UnifiedTree tree = new UnifiedTree(this);
		tree.accept(visitor, DEPTH_INFINITE);
		/* if force is false and resources were not in sync, throw an exception */
		if (!force)
			if (!visitor.getStatus().isOK())
				throw new ResourceException(visitor.getStatus());
		getLocalManager().move(this, destination, keepHistory, Policy.subMonitorFor(monitor, 70));
	} finally {
		monitor.done();
	}
}
/**
 * @see IResource#refreshLocal
 */
public void refreshLocal(int depth, IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask(Policy.bind("refreshing", new String[] { getFullPath().toString()}), Policy.totalWork);
		boolean build = false;
		try {
			workspace.prepareOperation();
			if (getType() != ROOT && !getProject().isAccessible())
				return;
			workspace.beginOperation(true);
			build = getLocalManager().refresh(this, depth, Policy.subMonitorFor(monitor, Policy.opWork));
		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(build, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
/**
 * @see IResource
 * @deprecated since 0.104 use #setLocal(boolean, int, IProgressMonitor)
 */
public void setLocal(boolean flag, int depth) {
	try {
		setLocal(flag, depth, null);
	} catch (CoreException e) {
		// FIXME: decide what to do here. log? throw? nothing?
		ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
	}
}
/**
 * @see IResource
 */
public void setLocal(boolean flag, int depth, IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask("Setting resource local flag.", Policy.totalWork);
		try {
			workspace.prepareOperation();
			workspace.beginOperation(true);
			internalSetLocal(flag, depth);
			monitor.worked(Policy.opWork);
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
/**
 * @see IResource
 */
public void setPersistentProperty(QualifiedName key, String value) throws CoreException {
	ResourceInfo info = getResourceInfo(false, false);
	int flags = getFlags(info);
	checkAccessible(flags);
	checkLocal(flags, DEPTH_ZERO);
	getPropertyManager().setProperty(this, key, value);
}
/**
 * @see IResource
 */
public void setReadOnly(boolean readonly) {
	CoreFileSystemLibrary.setReadOnly(getLocation().toOSString(), readonly);
}
/**
 * @see IResource
 */
public void setSessionProperty(QualifiedName key, Object value) throws CoreException {
	// fetch the info but don't bother making it mutable even though we are going
	// to modify it.  We don't know whether or not the tree is open and it really doesn't
	// matter as the change we are doing does not show up in deltas.
	ResourceInfo info = getResourceInfo(false, false);
	int flags = getFlags(info);
	checkAccessible(flags);
	checkLocal(flags, DEPTH_ZERO);
	info.setSessionProperty(key, value);
}
/**
 * Returns true if this resource has the potential to be
 * (or have been) synchronized.  
 */
public boolean synchronizing(ResourceInfo info) {
	return info != null && info.getSyncInfo(false) != null;
}
public String toString() {
	return getTypeString() + getFullPath().toString();
}
/**
 * @see IResource
 */
public void touch(IProgressMonitor monitor) throws CoreException {
	monitor = Policy.monitorFor(monitor);
	try {
		monitor.beginTask("Touching", Policy.totalWork);
		try {
			workspace.prepareOperation();
			ResourceInfo info = getResourceInfo(false, false);
			int flags = getFlags(info);
			checkAccessible(flags);
			checkLocal(flags, DEPTH_ZERO);

			workspace.beginOperation(true);
			// fake a change by incrementing the content ID
			info = getResourceInfo(false, true);
			info.incrementContentId();
			workspace.updateModificationStamp(info);
			monitor.worked(Policy.opWork);
		} catch (OperationCanceledException e) {
			workspace.getWorkManager().operationCanceled();
			throw e;
		} finally {
			workspace.endOperation(true, Policy.subMonitorFor(monitor, Policy.buildWork));
		}
	} finally {
		monitor.done();
	}
}
}
