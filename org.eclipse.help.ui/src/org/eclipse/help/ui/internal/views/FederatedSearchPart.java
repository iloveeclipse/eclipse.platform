/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.help.ui.internal.views;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.help.ui.internal.*;
import org.eclipse.help.ui.internal.HelpUIResources;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.forms.*;
import org.eclipse.ui.forms.AbstractFormPart;
import org.eclipse.ui.forms.widgets.*;

public class FederatedSearchPart extends AbstractFormPart implements IHelpPart {
	private static final String ENGINE_EXP_ID = "org.eclipse.help.ui.searchEngine";
	private static final String ATT_LABEL ="label"; //$NON-NLS-1$
	private static final String ATT_ICON = "icon";//$NON-NLS-1$
	private static final String EL_DESC = "description"; //$NON-NLS-1$
	private ReusableHelpPart parent;
	protected static java.util.List previousSearchQueryData = new java.util.ArrayList(
			20);
	private String id;
	private Composite container;
	private Combo searchWordCombo;
	private Button goButton;

	/**
	 * @param parent
	 * @param toolkit
	 * @param style
	 */
	public FederatedSearchPart(Composite parent, FormToolkit toolkit) {
		container = toolkit.createComposite(parent);
		TableWrapLayout layout = new TableWrapLayout();
		layout.numColumns = 2;
		container.setLayout(layout);
		// Search Expression
		Label expressionLabel = toolkit.createLabel(container, null, SWT.WRAP);
		expressionLabel.setText(HelpUIResources.getString("expression")); //$NON-NLS-1$
		TableWrapData td = new TableWrapData();
		td.colspan = 2;
		expressionLabel.setLayoutData(td);
		// Pattern combo
		searchWordCombo = new Combo(container, SWT.SINGLE | SWT.BORDER);
		td = new TableWrapData(TableWrapData.FILL_GRAB);
		//td.widthHint = 50;//convertWidthInCharsToPixels(30);
		searchWordCombo.setLayoutData(td);
		// Not done here to prevent page from resizing
		// fPattern.setItems(getPreviousSearchPatterns());
		searchWordCombo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (searchWordCombo.getSelectionIndex() < 0)
					return;
				int index = previousSearchQueryData.size() - 1
						- searchWordCombo.getSelectionIndex();
				/*
				searchQueryData = (SearchQueryData) previousSearchQueryData
						.get(index);
				searchWordCombo.setText(searchQueryData.getSearchWord());
				all.setSelection(!searchQueryData.isBookFiltering());
				selected.setSelection(searchQueryData.isBookFiltering());
				includeDisabledActivities.setSelection(!searchQueryData
						.isActivityFiltering());
				displaySelectedBooks();
				// headingsButton.setSelection(searchOperation.getQueryData().isFieldsSearch());
				 * 
				 */
			}
		});
		goButton = toolkit.createButton(container,
				"Go", SWT.PUSH); //$NON-NLS-1$
		goButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				doSearch(searchWordCombo.getText());
			}
		});
		goButton.setEnabled(false);
		searchWordCombo.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				goButton.setEnabled(searchWordCombo.getText().length()>0);
			}
		});
		searchWordCombo.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (e.character == '\r') {
					if (goButton.isEnabled())
						doSearch(searchWordCombo.getText());
				}
			}
		});
		// Space
		//toolkit.createLabel(control, null);
		// Syntax description
		Label label = toolkit.createLabel(container, null, SWT.WRAP);
		label.setText(HelpUIResources.getString("expression_label").replace('\n', ' ')); //$NON-NLS-1$
		// Filtering group
		toolkit.createLabel(container, null);
		td = new TableWrapData();
		td.colspan = 2;
		Section filteringComposite = toolkit.createSection(container, 
				Section.TWISTIE|Section.COMPACT);
		td = new TableWrapData();
		td.colspan = 2;
		filteringComposite.setLayoutData(td);
		Composite filteringGroup = toolkit.createComposite(filteringComposite);
		filteringComposite.setClient(filteringGroup);
		TableWrapLayout flayout = new TableWrapLayout();
		flayout.numColumns = 2;
		filteringComposite.setText(HelpUIResources.getString("limit_to")); //$NON-NLS-1$
		filteringGroup.setLayout(flayout);

		toolkit.paintBordersFor(filteringGroup);
		loadEngines(filteringGroup, toolkit);
		Hyperlink advanced = toolkit.createHyperlink(filteringGroup, "Advanced Settings", SWT.NULL);
		td = new TableWrapData();
		td.colspan = 2;
		advanced.setLayoutData(td);
	}
	
	private void loadEngines(Composite container, FormToolkit toolkit) {
		IConfigurationElement [] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(ENGINE_EXP_ID);
		for (int i=0; i<elements.length; i++) {
			IConfigurationElement element = elements[i];
			if (element.getName().equals("engine")) {
				loadEngine(element, container, toolkit);
			}
		}
	}
	
	private void loadEngine(IConfigurationElement element, Composite container, FormToolkit toolkit) {
		String name = element.getAttribute(ATT_LABEL);
		String desc = null;
		IConfigurationElement [] children = element.getChildren(EL_DESC);
		if (children.length==1) 
			desc = children[0].getValue();
		String icon = element.getAttribute(ATT_ICON);
		Image image = null;
		
		if (icon!=null)
			image = HelpUIResources.getImage(icon);
		else
			image = HelpUIResources.getImage(IHelpUIConstants.IMAGE_HELP_SEARCH);
		Label ilabel = toolkit.createLabel(container, null);
		ilabel.setImage(image);
		Button master = toolkit.createButton(container, name, SWT.CHECK);
		if (desc!=null) {
			toolkit.createLabel(container, null);
			Label dlabel = toolkit.createLabel(container, desc, SWT.WRAP);
			dlabel.setForeground(toolkit.getColors().getColor(FormColors.TITLE));
			dlabel.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
		}
	}
	
	private void doSearch(String text) {
		
	}
	
	public void dispose() {
		super.dispose();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#getControl()
	 */
	public Control getControl() {
		return container;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#init(org.eclipse.help.ui.internal.views.NewReusableHelpPart)
	 */
	public void init(ReusableHelpPart parent, String id) {
		this.parent = parent;
		this.id = id;
	}

	public String getId() {
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
		getControl().setVisible(visible);
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#fillContextMenu(org.eclipse.jface.action.IMenuManager)
	 */
	public boolean fillContextMenu(IMenuManager manager) {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#hasFocusControl(org.eclipse.swt.widgets.Control)
	 */
	public boolean hasFocusControl(Control control) {
		return false;
	}
}