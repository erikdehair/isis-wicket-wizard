/*
 *  Copyright 2014 Dan Haywood
 *
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.isisaddons.wicket.wizard.cpt.ui;

import java.util.List;
import java.util.Map;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.ComponentFeedbackPanel;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.isis.applib.annotation.MemberGroupLayout.ColumnSpans;
import org.apache.isis.applib.annotation.Where;
import org.apache.isis.applib.filter.Filter;
import org.apache.isis.applib.filter.Filters;
import org.apache.isis.applib.services.exceprecog.ExceptionRecognizer;
import org.apache.isis.applib.services.exceprecog.ExceptionRecognizerComposite;
import org.apache.isis.core.commons.authentication.MessageBroker;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.mgr.AdapterManager.ConcurrencyChecking;
import org.apache.isis.core.metamodel.adapter.version.ConcurrencyException;
import org.apache.isis.core.metamodel.facets.object.membergroups.MemberGroupLayoutFacet;
import org.apache.isis.core.metamodel.facets.object.wizard.WizardFacet;
import org.apache.isis.core.metamodel.runtimecontext.ServicesInjector;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.ObjectSpecifications;
import org.apache.isis.core.metamodel.spec.ObjectSpecifications.MemberGroupLayoutHint;
import org.apache.isis.core.metamodel.spec.feature.Contributed;
import org.apache.isis.core.metamodel.spec.feature.ObjectAssociation;
import org.apache.isis.core.metamodel.spec.feature.OneToOneAssociation;
import org.apache.isis.core.runtime.memento.Memento;
import org.apache.isis.core.runtime.system.context.IsisContext;
import org.apache.isis.core.runtime.system.transaction.IsisTransactionManager;
import org.apache.isis.viewer.wicket.model.mementos.PropertyMemento;
import org.apache.isis.viewer.wicket.model.models.EntityModel;
import org.apache.isis.viewer.wicket.model.models.ScalarModel;
import org.apache.isis.viewer.wicket.ui.ComponentType;
import org.apache.isis.viewer.wicket.ui.components.widgets.breadcrumbs.BreadcrumbModel;
import org.apache.isis.viewer.wicket.ui.components.widgets.breadcrumbs.BreadcrumbModelProvider;
import org.apache.isis.viewer.wicket.ui.components.widgets.containers.UiHintPathSignificantWebMarkupContainer;
import org.apache.isis.viewer.wicket.ui.errors.JGrowlBehaviour;
import org.apache.isis.viewer.wicket.ui.pages.PageAbstract;
import org.apache.isis.viewer.wicket.ui.pages.entity.EntityPage;
import org.apache.isis.viewer.wicket.ui.pages.home.HomePage;
import org.apache.isis.viewer.wicket.ui.panels.FormAbstract;
import org.apache.isis.viewer.wicket.ui.panels.IFormSubmitterWithPreValidateHook;
import org.apache.isis.viewer.wicket.ui.util.CssClassAppender;

public class WizardPropertiesForm extends FormAbstract<ObjectAdapter> {

    private static final long serialVersionUID = 1L;

    private static final String ID_MEMBER_GROUP = "memberGroup";
    private static final String ID_MEMBER_GROUP_NAME = "memberGroupName";

    private static final String ID_LEFT_COLUMN = "leftColumn";

    private static final String ID_ENTITY_COLLECTIONS_OVERFLOW = "entityCollectionsOverflow";
    
    private static final String ID_PROPERTIES = "properties";
    private static final String ID_PROPERTY = "property";

    private static final String ID_WIZARD_NEXT_BUTTON = "wizardNext";
    private static final String ID_WIZARD_PREVIOUS_BUTTON = "wizardPrevious";
    private static final String ID_WIZARD_FINISH_BUTTON = "wizardFinish";
    private static final String ID_WIZARD_CANCEL_BUTTON = "wizardCancel";

    private static final String ID_FEEDBACK = "feedback";

    private final Component owningPanel;

    private Button wizardNextButton;
    private Button wizardPreviousButton;
    private Button wizardFinishButton;
    private Button wizardCancelButton;

    private FeedbackPanel feedback;
    
    private boolean renderedFirstField;

    public WizardPropertiesForm(final String id, final EntityModel entityModel, final Component owningPanel) {
        super(id, entityModel);
        this.owningPanel = owningPanel; // for repainting

        buildGui();
        
        // add any concurrency exception that might have been propagated into the entity model 
        // as a result of a previous action invocation
        final String concurrencyExceptionIfAny = entityModel.getAndClearConcurrencyExceptionIfAny();
        if(concurrencyExceptionIfAny != null) {
            error(concurrencyExceptionIfAny);
        }
    }

    private void buildGui() {

        final EntityModel entityModel = (EntityModel) getModel();
        final ColumnSpans columnSpans = entityModel.getObject().getSpecification().getFacet(MemberGroupLayoutFacet.class).getColumnSpans();

        renderedFirstField = false;
        
        final MarkupContainer leftColumn = new WebMarkupContainer(ID_LEFT_COLUMN);
        add(leftColumn);
        
        addPropertiesInColumn(leftColumn, MemberGroupLayoutHint.LEFT, columnSpans);
        addWizardButtons(leftColumn);
        addFeedbackGui(leftColumn);

        // collections
        final Component collectionsColumn = getComponentFactoryRegistry().addOrReplaceComponent(
                this, ID_ENTITY_COLLECTIONS_OVERFLOW, ComponentType.ENTITY_COLLECTIONS, entityModel);
        addClassForSpan(collectionsColumn, 12);
    }


    private boolean addPropertiesInColumn(MarkupContainer markupContainer, MemberGroupLayoutHint hint, ColumnSpans columnSpans) {
        final int span = hint.from(columnSpans);
        
        final EntityModel entityModel = (EntityModel) getModel();
        final ObjectAdapter adapter = entityModel.getObject();
        final ObjectSpecification objSpec = adapter.getSpecification();

        final List<ObjectAssociation> associations = visibleProperties(adapter, objSpec, Where.OBJECT_FORMS);

        final RepeatingView memberGroupRv = new RepeatingView(ID_MEMBER_GROUP);
        markupContainer.add(memberGroupRv);

        final Map<String, List<ObjectAssociation>> associationsByGroup = ObjectAssociation.Util.groupByMemberOrderName(associations);
        
        final List<String> groupNames = ObjectSpecifications.orderByMemberGroups(objSpec, associationsByGroup.keySet(), hint);
        
        for(final String groupName: groupNames) {
            final List<ObjectAssociation> associationsInGroup = associationsByGroup.get(groupName);
            if(associationsInGroup==null) {
                continue;
            }

            final WebMarkupContainer memberGroupRvContainer = new WebMarkupContainer(memberGroupRv.newChildId());
            memberGroupRv.add(memberGroupRvContainer);
            memberGroupRvContainer.add(new Label(ID_MEMBER_GROUP_NAME, groupName));

            final RepeatingView propertyRv = new RepeatingView(ID_PROPERTIES);
            memberGroupRvContainer.add(propertyRv);

            for (final ObjectAssociation association : associationsInGroup) {
                final WebMarkupContainer propertyRvContainer = new UiHintPathSignificantWebMarkupContainer(propertyRv.newChildId());
                propertyRv.add(propertyRvContainer);
                addPropertyToForm(entityModel, association, propertyRvContainer);
            }
        }
        
        addClassForSpan(markupContainer, span);
        return !groupNames.isEmpty();
    }

    private void addPropertyToForm(
            final EntityModel entityModel,
            final ObjectAssociation association,
            final WebMarkupContainer container) {
        final OneToOneAssociation otoa = (OneToOneAssociation) association;
        final PropertyMemento pm = new PropertyMemento(otoa);

        final ScalarModel scalarModel = entityModel.getPropertyModel(pm);
        final Component component = getComponentFactoryRegistry().addOrReplaceComponent(container, ID_PROPERTY, ComponentType.SCALAR_NAME_AND_VALUE, scalarModel);
        
        if(!renderedFirstField) {
            component.add(new CssClassAppender("first-field"));
            renderedFirstField = true;
        }
    }

    private List<ObjectAssociation> visibleProperties(final ObjectAdapter adapter, final ObjectSpecification objSpec, Where where) {
        return objSpec.getAssociations(Contributed.INCLUDED, visiblePropertyFilter(adapter, where));
    }

    @SuppressWarnings("unchecked")
    private Filter<ObjectAssociation> visiblePropertyFilter(final ObjectAdapter adapter, Where where) {
        return Filters.and(ObjectAssociation.Filters.PROPERTIES, ObjectAssociation.Filters.dynamicallyVisible(getAuthenticationSession(), adapter, where));
    }


    private abstract class AjaxButtonWithOnError extends AjaxButton {

        public AjaxButtonWithOnError(String id, IModel<String> model) {
            super(id, model);
        }

        @Override
        protected void onError(AjaxRequestTarget target, Form<?> form) {
            super.onError(target, form);
            toEditMode(target);
        }
    }

    private abstract class AjaxButtonForValidate extends AjaxButtonWithOnError implements IFormSubmitterWithPreValidateHook {
        private static final long serialVersionUID = 1L;
        public AjaxButtonForValidate(String id, IModel<String> model) {
            super(id, model);
        }

        @Override
        public String preValidate() {
            // attempt to load with concurrency checking, catching recognized exceptions
            try {
                getEntityModel().load(ConcurrencyChecking.CHECK); // could have also just called #getObject(), since CHECK is the default

            } catch(ConcurrencyException ex){
                String recognizedErrorMessage = recognizeException(ex);
                if(recognizedErrorMessage == null) {
                    throw ex;
                }

                // reload
                getEntityModel().load(ConcurrencyChecking.NO_CHECK);

                getForm().clearInput();
                getEntityModel().resetPropertyModels();

                toEditMode(null);

                return recognizedErrorMessage;
            }

            return null;
        }

        @Override
        public void validate() {
            // add in any error message that we might have recognized from above
            WizardPropertiesForm form = WizardPropertiesForm.this;
            String preValidationErrorIfAny = form.getPreValidationErrorIfAny();

            if(preValidationErrorIfAny != null) {
                feedbackOrNotifyAnyRecognizedError(preValidationErrorIfAny, form);
                // skip validation, because would relate to old values

                final EntityPage entityPage = new EntityPage(WizardPropertiesForm.this.getModelObject(), null);
                WizardPropertiesForm.this.setResponsePage(entityPage);
            } else {
                // run Wicket's validation
                super.validate();
            }
        }

        @Override
        protected void onSubmit(AjaxRequestTarget target, Form<?> form) {

            if (getForm().hasError()) {
                // stay in edit mode
                return;
            }

            doPreApply();
            if (applyFormChangesElse()) return;
            final Object redirectIfAny = doPostApply();

            if (flushChangesElse(target)) return;

            getEntityModel().resetPropertyModels();

            toEditMode(null);

            // "redirect-after-post"
            //
            // RequestCycle.get().getActiveRequestHandler() indicates this is handled by the ListenerInterfaceRequestHandler
            // which renders page at end.
            //
            // it's necessary to zap the page parameters (so mapping is to just wicket/page?nn)
            // otherwise (what I think happens) is that the httpServletResponse.sendRedirect ends up being to the same URL,
            // and this is rejected as invalid either by the browser or by the servlet container (perhaps only if running remotely).
            //

            final ObjectAdapter objectAdapter;
            if(redirectIfAny != null) {
                objectAdapter = getPersistenceSession().getAdapterManager().adapterFor(redirectIfAny);
            } else {
                // we obtain the adapter from the entity model because (if a view model) then the entity model may contain
                // a different adapter (the cloned view model) to the one with which we started with.
                objectAdapter = getEntityModel().getObjectAdapterMemento().getObjectAdapter(ConcurrencyChecking.NO_CHECK);
            }

            final EntityPage entityPage = new EntityPage(objectAdapter, null);
            WizardPropertiesForm.this.setResponsePage(entityPage);
        }

        /**
         * Optional hook to override.
         *
         * <p>
         * If a non-null value is returned, then transition to it (ie eg the finish() transition for a wizard).
         * </p>
         */
        protected void doPreApply() {
        }

        /**
         * Optional hook to override.
         *
         * <p>
         * If a non-null value is returned, then transition to it (ie eg the finish() transition for a wizard).
         * </p>
         */
        protected Object doPostApply() {
            return null;
        }

    }

    private void addWizardButtons(MarkupContainer markupContainer) {

        // next
        wizardNextButton = new AjaxButtonForValidate(ID_WIZARD_NEXT_BUTTON, Model.of("Next")) {
            private static final long serialVersionUID = 1L;
            @Override
            protected void doPreApply() {
                final ObjectAdapter adapter = getEntityModel().getObject();
                final WizardFacet wizardFacet = adapter.getSpecification().getFacet(WizardFacet.class);
                wizardFacet.next(adapter.getObject());
            }
        };
        markupContainer.add(wizardNextButton);

        wizardPreviousButton = new AjaxButtonForValidate(ID_WIZARD_PREVIOUS_BUTTON, Model.of("Previous")) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doPreApply() {
                final ObjectAdapter adapter = getEntityModel().getObject();
                final WizardFacet wizardFacet = adapter.getSpecification().getFacet(WizardFacet.class);
                wizardFacet.previous(adapter.getObject());
            }
        };
        markupContainer.add(wizardPreviousButton);


        wizardFinishButton = new AjaxButtonForValidate(ID_WIZARD_FINISH_BUTTON, Model.of("Finish")) {
            private static final long serialVersionUID = 1L;

            @Override
            protected Object doPostApply() {
                final ObjectAdapter adapter = getEntityModel().getObject();
                final WizardFacet wizardFacet = adapter.getSpecification().getFacet(WizardFacet.class);
                return wizardFacet.finish(adapter.getObject());
            }
        };
        markupContainer.add(wizardFinishButton);


        wizardCancelButton = new Button(ID_WIZARD_CANCEL_BUTTON, Model.of("Cancel")) {

            private static final long serialVersionUID = 1L;
            {
                setDefaultFormProcessing(false);
            }

            public void onSubmit() {
                final PageAbstract page = determinePageToRedirectTo();
                WizardPropertiesForm.this.setResponsePage(page);
            }

            private PageAbstract determinePageToRedirectTo() {

                // go to the most recently viewed entity (if available) ...
                final List<EntityModel> list = getBreadcrumbs();
                for (EntityModel entityModel : list) {
                    ObjectSpecification objectSpec = entityModel.getTypeOfSpecification();
                    if(!objectSpec.isViewModel()) {
                        ObjectAdapter objectAdapter = entityModel.getObjectAdapterMemento().getObjectAdapter(ConcurrencyChecking.NO_CHECK);

                        return new EntityPage(objectAdapter, null);
                    }
                }

                // ... else go to the home page
                return new HomePage(new PageParameters());
            }

            private List<EntityModel> getBreadcrumbs() {
                final BreadcrumbModelProvider session = (BreadcrumbModelProvider) getSession();
                final BreadcrumbModel breadcrumbModel = session.getBreadcrumbModel();
                return breadcrumbModel.getList();
            }
        };
        markupContainer.add(wizardCancelButton);


        wizardNextButton.setOutputMarkupPlaceholderTag(true);
        wizardPreviousButton.setOutputMarkupPlaceholderTag(true);
        wizardFinishButton.setOutputMarkupPlaceholderTag(true);
        wizardCancelButton.setOutputMarkupPlaceholderTag(true);

        // flush any JGrowl messages if they are added.
        wizardNextButton.add(new JGrowlBehaviour());
        wizardPreviousButton.add(new JGrowlBehaviour());
        wizardFinishButton.add(new JGrowlBehaviour());
        wizardCancelButton.add(new JGrowlBehaviour());
    }

    // to perform object-level validation, we must apply the changes first
    // contrast this with ActionPanel (for validating actionarguments) where
    // we do the validation prior to the execution of the action
    private boolean applyFormChangesElse() {
        final ObjectAdapter adapter = getEntityModel().getObject();
        final Memento snapshotToRollbackToIfInvalid = new Memento(adapter);

        getEntityModel().apply();
        final String invalidReasonIfAny = getEntityModel().getReasonInvalidIfAny();
        if (invalidReasonIfAny != null) {
            error(invalidReasonIfAny);
            snapshotToRollbackToIfInvalid.recreateObject();
            toEditMode(null);
            return true;
        }
        return false;
    }

    private boolean flushChangesElse(AjaxRequestTarget target) {
        try {
            this.getTransactionManager().flushTransaction();
        } catch(RuntimeException ex) {

            // There's no need to abort the transaction here, as it will have already been done
            // (in IsisTransactionManager#executeWithinTransaction(...)).

            String message = recognizeExceptionAndNotify(ex, this);
            if(message == null) {
                throw ex;
            }

            toEditMode(target);
            return true;
        }
        return false;
    }

    private String recognizeExceptionAndNotify(RuntimeException ex, Component feedbackComponentIfAny) {
        // see if the exception is recognized as being a non-serious error
        
        String recognizedErrorMessageIfAny = recognizeException(ex);
        feedbackOrNotifyAnyRecognizedError(recognizedErrorMessageIfAny, feedbackComponentIfAny);

        return recognizedErrorMessageIfAny;
    }

    private void feedbackOrNotifyAnyRecognizedError(String recognizedErrorMessageIfAny, Component feedbackComponentIfAny) {
        if(recognizedErrorMessageIfAny == null) {
            return;
        }
        
        if(feedbackComponentIfAny != null) {
            feedbackComponentIfAny.error(recognizedErrorMessageIfAny);
        }
        getMessageBroker().addWarning(recognizedErrorMessageIfAny);

        // we clear the abort cause because we've handled rendering the exception
        getTransactionManager().getTransaction().clearAbortCause();
    }

    private String recognizeException(RuntimeException ex) {
        
        final List<ExceptionRecognizer> exceptionRecognizers = getServicesInjector().lookupServices(ExceptionRecognizer.class);
        final String message = new ExceptionRecognizerComposite(exceptionRecognizers).recognize(ex);
        return message;
    }

    private void requestRepaintPanel(final AjaxRequestTarget target) {
        if (target != null) {
            target.add(owningPanel);
        }
    }

    private EntityModel getEntityModel() {
        return (EntityModel) getModel();
    }

    void toEditMode(final AjaxRequestTarget target) {
        getEntityModel().toViewMode();
        getEntityModel().toEditMode();

        // wizard handling.
        final ObjectAdapter adapter = getEntityModel().getObject();
        final WizardFacet wizardFacet = adapter.getSpecification().getFacet(WizardFacet.class);
        disableIfRequired(wizardNextButton, wizardFacet.disableNext(adapter.getObject()));
        final ObjectAdapter adapter1 = getEntityModel().getObject();
        final WizardFacet wizardFacet1 = adapter1.getSpecification().getFacet(WizardFacet.class);
        disableIfRequired(wizardPreviousButton, wizardFacet1.disablePrevious(adapter1.getObject()));
        final ObjectAdapter adapter2 = getEntityModel().getObject();
        final WizardFacet wizardFacet2 = adapter2.getSpecification().getFacet(WizardFacet.class);
        disableIfRequired(wizardFinishButton, wizardFacet2.disableFinish(adapter2.getObject()));

        requestRepaintPanel(target);
    }

    private void disableIfRequired(Button button, String disabledReason) {
        if(disabledReason != null) {
            button.setEnabled(false);
            button.add(new AttributeAppender("title", disabledReason));
        }
    }

    private void addFeedbackGui(MarkupContainer markupContainer) {
        feedback = new ComponentFeedbackPanel(ID_FEEDBACK, this);
        feedback.setOutputMarkupPlaceholderTag(true);
        markupContainer.addOrReplace(feedback);
        feedback.setEscapeModelStrings(false);

        final ObjectAdapter adapter = getEntityModel().getObject();
        if (adapter == null) {
            feedback.error("cannot locate object:" + getEntityModel().getObjectAdapterMemento().toString());
        }
    }

    private static void addClassForSpan(final Component component, final int numGridCols) {
        component.add(new CssClassAppender("span"+numGridCols));
    }


    ///////////////////////////////////////////////////////
    // Dependencies (from context)
    ///////////////////////////////////////////////////////
    
    protected IsisTransactionManager getTransactionManager() {
        return IsisContext.getTransactionManager();
    }

    protected ServicesInjector getServicesInjector() {
        return IsisContext.getPersistenceSession().getServicesInjector();
    }

    protected MessageBroker getMessageBroker() {
        return getAuthenticationSession().getMessageBroker();
    }

}