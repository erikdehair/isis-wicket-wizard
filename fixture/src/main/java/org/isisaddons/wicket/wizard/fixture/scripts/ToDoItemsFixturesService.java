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
package org.isisaddons.wicket.wizard.fixture.scripts;

import java.util.List;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.Named;
import org.apache.isis.applib.annotation.Prototype;
import org.apache.isis.applib.fixturescripts.FixtureResult;
import org.apache.isis.applib.fixturescripts.FixtureScript;
import org.apache.isis.applib.fixturescripts.FixtureScripts;

@Named("Prototyping")
@DomainService(menuOrder = "90")
public class ToDoItemsFixturesService extends FixtureScripts {

    public ToDoItemsFixturesService() {
        super("org.isisaddons.wicket.wizard.fixture");
    }

    @Override
    public FixtureScript default0RunFixtureScript() {
        return findFixtureScriptFor(WizardWicketAppSetUpFixture.class);
    }

    /**
     * Raising visibility to <tt>public</tt> so that choices are available for first param
     * of {@link #runFixtureScript(FixtureScript, String)}.
     */
    @Override
    public List<FixtureScript> choices0RunFixtureScript() {
        return super.choices0RunFixtureScript();
    }

    // //////////////////////////////////////


    @Prototype
    @MemberOrder(sequence="20")
    public Object recreateToDoItemsForCurrentAndReturnFirst() {
        final List<FixtureResult> run = findFixtureScriptFor(WizardWicketAppSetUpFixture.class).run(null);
        return run.get(0).getObject();
    }
}
