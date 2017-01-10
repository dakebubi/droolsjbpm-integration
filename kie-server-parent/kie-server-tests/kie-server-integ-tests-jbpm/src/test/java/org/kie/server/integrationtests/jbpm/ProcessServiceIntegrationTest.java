/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.kie.server.integrationtests.jbpm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.kie.api.KieServices;
import org.kie.api.task.model.Status;
import org.kie.internal.KieInternalServices;
import org.kie.internal.executor.api.STATUS;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.process.CorrelationKeyFactory;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.RequestInfoInstance;
import org.kie.server.api.model.instance.TaskInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.api.model.instance.WorkItemInstance;
import org.kie.server.client.KieServicesException;
import org.kie.server.integrationtests.category.Smoke;
import org.kie.server.integrationtests.config.TestConfig;

import static org.junit.Assert.*;
import org.kie.server.integrationtests.shared.KieServerDeployer;
import org.kie.server.integrationtests.shared.KieServerSynchronization;


public class ProcessServiceIntegrationTest extends JbpmKieServerBaseIntegrationTest {

    private static ReleaseId releaseId = new ReleaseId("org.kie.server.testing", "definition-project",
            "1.0.0.Final");


    @BeforeClass
    public static void buildAndDeployArtifacts() {

        KieServerDeployer.buildAndDeployCommonMavenParent();
        KieServerDeployer.buildAndDeployMavenProject(ClassLoader.class.getResource("/kjars-sources/definition-project").getFile());

        kieContainer = KieServices.Factory.get().newKieContainer(releaseId);

        createContainer(CONTAINER_ID, releaseId);
    }

    @Override
    protected void addExtraCustomClasses(Map<String, Class<?>> extraClasses) throws Exception {
        extraClasses.put(PERSON_CLASS_NAME, Class.forName(PERSON_CLASS_NAME, true, kieContainer.getClassLoader()));
    }

    @Test
    public void testStartCheckVariablesAndAbortProcess() throws Exception {
        Class<?> personClass = Class.forName(PERSON_CLASS_NAME, true, kieContainer.getClassLoader());

        Object person = createPersonInstance(USER_JOHN);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("test", USER_MARY);
        parameters.put("number", new Integer(12345));

        List<Object> list = new ArrayList<Object>();
        list.add("item");

        parameters.put("list", list);
        parameters.put("person", person);
        Long processInstanceId = null;
        try {
            processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, parameters);

            assertNotNull(processInstanceId);
            assertTrue(processInstanceId.longValue() > 0);

            Object personVariable = processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "person");
            assertNotNull(personVariable);
            assertTrue(personClass.isAssignableFrom(personVariable.getClass()));

            personVariable = processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "person");
            assertNotNull(personVariable);
            assertTrue(personClass.isAssignableFrom(personVariable.getClass()));

            Map<String, Object> variables = processClient.getProcessInstanceVariables(CONTAINER_ID, processInstanceId);
            assertNotNull(variables);
            assertEquals(5, variables.size());
            assertTrue(variables.containsKey("test"));
            assertTrue(variables.containsKey("number"));
            assertTrue(variables.containsKey("list"));
            assertTrue(variables.containsKey("person"));
            assertTrue(variables.containsKey("initiator"));

            assertNotNull(variables.get("test"));
            assertNotNull(variables.get("number"));
            assertNotNull(variables.get("list"));
            assertNotNull(variables.get("person"));
            assertNotNull(variables.get("initiator"));

            assertTrue(String.class.isAssignableFrom(variables.get("test").getClass()));
            assertTrue(Integer.class.isAssignableFrom(variables.get("number").getClass()));
            assertTrue(List.class.isAssignableFrom(variables.get("list").getClass()));
            assertTrue(personClass.isAssignableFrom(variables.get("person").getClass()));
            assertTrue(String.class.isAssignableFrom(variables.get("initiator").getClass()));

            assertEquals(USER_MARY, variables.get("test"));
            assertEquals(12345, variables.get("number"));
            assertEquals(1, ((List) variables.get("list")).size());
            assertEquals("item", ((List) variables.get("list")).get(0));
            assertEquals(USER_JOHN, valueOf(variables.get("person"), "name"));
            assertEquals(TestConfig.getUsername(), variables.get("initiator"));
        } finally {
            if (processInstanceId != null) {
                processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            }
        }



    }

    @Test(expected = KieServicesException.class)
    public void testStartNotExistingProcess() {
        processClient.startProcess(CONTAINER_ID, "not-existing", (Map)null);
    }

    @Test()
    public void testAbortExistingProcess() {
        Map<String, Object> parameters = new HashMap<String, Object>();

        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, parameters);
        try {
            assertNotNull(processInstanceId);
            assertTrue(processInstanceId.longValue() > 0);

            // Process instance is running and is active.
            ProcessInstance processInstance = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(processInstance);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE, processInstance.getState().intValue());

            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);

            // Process instance is now aborted.
            processInstance = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(processInstance);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_ABORTED, processInstance.getState().intValue());
        } catch (Exception e) {
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            fail(e.getMessage());
        }
    }

    @Test(expected = KieServicesException.class)
    public void testAbortNonExistingProcess() {
        processClient.abortProcessInstance(CONTAINER_ID, 9999l);
    }

    @Test(expected = KieServicesException.class)
    public void testStartCheckNonExistingVariables() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, parameters);
        try {
            processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "person");
        } finally {
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
        }

    }

    @Test
    public void testAbortMultipleProcessInstances() throws Exception {

        Long processInstanceId1 = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION);
        Long processInstanceId2 = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION);
        Long processInstanceId3 = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION);
        Long processInstanceId4 = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION);

        List<Long> processInstances = new ArrayList<Long>();
        processInstances.add(processInstanceId1);
        processInstances.add(processInstanceId2);
        processInstances.add(processInstanceId3);
        processInstances.add(processInstanceId4);

        processClient.abortProcessInstances(CONTAINER_ID, processInstances);
    }

    @Test
    public void testSignalProcessInstance() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);

        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            checkAvailableSignals(CONTAINER_ID, processInstanceId);

            Object person = createPersonInstance(USER_JOHN);
            processClient.signalProcessInstance(CONTAINER_ID, processInstanceId, "Signal1", person);

            processClient.signalProcessInstance(CONTAINER_ID, processInstanceId, "Signal2", "My custom string event");
        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    @Test
    public void testSignalProcessInstanceNullEvent() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            checkAvailableSignals(CONTAINER_ID, processInstanceId);

            processClient.signalProcessInstance(CONTAINER_ID, processInstanceId, "Signal1", null);

            processClient.signalProcessInstance(CONTAINER_ID, processInstanceId, "Signal2", null);
        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    @Test
    public void testSignalProcessInstances() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);

        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        Long processInstanceId2 = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);
        assertNotNull(processInstanceId2);
        assertTrue(processInstanceId2.longValue() > 0);

        List<Long> processInstanceIds = new ArrayList<Long>();
        processInstanceIds.add(processInstanceId);
        processInstanceIds.add(processInstanceId2);

        try {
            checkAvailableSignals(CONTAINER_ID, processInstanceId);
            checkAvailableSignals(CONTAINER_ID, processInstanceId2);

            Object person = createPersonInstance(USER_JOHN);
            processClient.signalProcessInstances(CONTAINER_ID, processInstanceIds, "Signal1", person);

            processClient.signalProcessInstances(CONTAINER_ID, processInstanceIds, "Signal2", "My custom string event");
        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId2);
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    @Test
    public void testManipulateProcessVariable() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);

        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            Object personVar = null;
            try {
                personVar = processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "personData");
                fail("Should fail as there is no process variable personData set yet");
            } catch (KieServicesException e) {
                // expected
            }
            assertNull(personVar);

            personVar = createPersonInstance(USER_JOHN);
            processClient.setProcessVariable(CONTAINER_ID, processInstanceId, "personData", personVar);

            personVar = processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "personData");
            assertNotNull(personVar);
            assertEquals(USER_JOHN, valueOf(personVar, "name"));


            processClient.setProcessVariable(CONTAINER_ID, processInstanceId, "stringData", "custom value");

            String stringVar = (String) processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "stringData");
            assertNotNull(personVar);
            assertEquals("custom value", stringVar);

        } finally {
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
        }

    }

    @Test
    public void testManipulateProcessVariables() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            Object personVar = null;
            String stringVar = null;
            try {
                personVar = processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "personData");
                fail("Should fail as there is no process variable personData set yet");
            } catch (KieServicesException e) {
                // expected
            }
            assertNull(personVar);

            try {
                stringVar = (String) processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "stringData");
                fail("Should fail as there is no process variable personData set yet");
            } catch (KieServicesException e) {
                // expected
            }
            assertNull(personVar);
            assertNull(stringVar);

            personVar = createPersonInstance(USER_JOHN);
            stringVar = "string variable test";

            Map<String, Object> variables = new HashMap<String, Object>();
            variables.put("personData", personVar);
            variables.put("stringData", stringVar);

            processClient.setProcessVariables(CONTAINER_ID, processInstanceId, variables);

            personVar = processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "personData");
            assertNotNull(personVar);
            assertEquals(USER_JOHN, valueOf(personVar, "name"));

            stringVar = (String) processClient.getProcessInstanceVariable(CONTAINER_ID, processInstanceId, "stringData");
            assertNotNull(personVar);
            assertEquals("string variable test", stringVar);

        } finally {
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
        }

    }

    @Test
    @Category(Smoke.class)
    public void testGetProcessInstance() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);

        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            ProcessInstance processInstance = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            ProcessInstance expectedInstance = createSignalProcessInstance(processInstanceId);
            assertProcessInstance(expectedInstance, processInstance);

            Map<String, Object> variables = processInstance.getVariables();
            assertNull(variables);
        } finally {
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
        }

    }

    @Test
    public void testGetProcessInstanceWithVariables() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("stringData", "waiting for signal");
        parameters.put("personData", createPersonInstance(USER_JOHN));

        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS, parameters);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            ProcessInstance processInstance = processClient.getProcessInstance(CONTAINER_ID, processInstanceId, true);
            ProcessInstance expectedInstance = createSignalProcessInstance(processInstanceId);
            assertProcessInstance(expectedInstance, processInstance);

            Map<String, Object> variables = processInstance.getVariables();
            assertNotNull(variables);
            assertEquals(3, variables.size());

            assertTrue(variables.containsKey("stringData"));
            assertTrue(variables.containsKey("personData"));
            assertTrue(variables.containsKey("initiator"));

            String stringVar = (String) variables.get("stringData");
            Object personVar = variables.get("personData");
            String initiator = (String) variables.get("initiator");

            assertNotNull(personVar);
            assertEquals(USER_JOHN, valueOf(personVar, "name"));

            assertNotNull(personVar);
            assertEquals("waiting for signal", stringVar);

            assertNotNull(initiator);
            assertEquals(TestConfig.getUsername(), initiator);

        } finally {
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
        }

    }

    @Test(expected = KieServicesException.class)
    public void testGetNonExistingProcessInstance() {
        processClient.getProcessInstance(CONTAINER_ID, 9999l);
    }

    @Test
    public void testWorkItemOperations() throws Exception {

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("person", createPersonInstance(USER_JOHN));

        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, parameters);
        try {
            assertNotNull(processInstanceId);
            assertTrue(processInstanceId.longValue() > 0);

            // Completing human task so we can move in process flow to work item.
            // User task shouldn't be handled as work item because in such case it doesn't behave consistently:
            // i.e. leaving open tasks after finishing process instance.
            List<String> status = Arrays.asList(Status.Ready.toString());
            List<TaskSummary> taskList = taskClient.findTasksByStatusByProcessInstanceId(processInstanceId, status, 0, 10);

            assertEquals(1, taskList.size());
            TaskSummary taskSummary = taskList.get(0);
            taskClient.startTask(CONTAINER_ID, taskSummary.getId(), USER_YODA);
            taskClient.completeTask(CONTAINER_ID, taskSummary.getId(), USER_YODA, null);

            TaskInstance userTask = taskClient.findTaskById(taskSummary.getId());
            assertNotNull(userTask);
            assertEquals("Evaluate items?", userTask.getName());
            assertEquals(Status.Completed.toString(), userTask.getStatus());

            List<WorkItemInstance> workItems = processClient.getWorkItemByProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(workItems);
            assertEquals(1, workItems.size());

            WorkItemInstance workItemInstance = workItems.get(0);
            assertNotNull(workItemInstance);
            assertEquals(processInstanceId, workItemInstance.getProcessInstanceId());
            assertEquals("Email", workItemInstance.getName());
            assertEquals(CONTAINER_ID, workItemInstance.getContainerId());
            assertEquals(0, workItemInstance.getState().intValue());
            assertEquals(5, workItemInstance.getParameters().size());

            assertNotNull(workItemInstance.getId());
            assertNotNull(workItemInstance.getNodeId());
            assertNotNull(workItemInstance.getProcessInstanceId());


            workItemInstance = processClient.getWorkItem(CONTAINER_ID, processInstanceId, workItemInstance.getId());
            assertNotNull(workItemInstance);
            assertEquals(processInstanceId, workItemInstance.getProcessInstanceId());
            assertEquals("Email", workItemInstance.getName());
            assertEquals(CONTAINER_ID, workItemInstance.getContainerId());
            assertEquals(0, workItemInstance.getState().intValue());
            assertEquals(5, workItemInstance.getParameters().size());

            assertNotNull(workItemInstance.getId());
            assertNotNull(workItemInstance.getNodeId());
            assertNotNull(workItemInstance.getProcessInstanceId());

            processClient.abortWorkItem(CONTAINER_ID, processInstanceId, workItemInstance.getId());

            ProcessInstance processInstance = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(processInstance);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, processInstance.getState().intValue());

        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            throw e;
        } finally {
            changeUser(TestConfig.getUsername());
        }
    }

    @Test
    public void testWorkItemOperationComplete() throws Exception {

        Map<String, Object> parameters = new HashMap<String, Object>();

        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, parameters);
        try {
            assertNotNull(processInstanceId);
            assertTrue(processInstanceId.longValue() > 0);

            // Completing human task so we can move in process flow to work item.
            // User task shouldn't be handled as work item because in such case it doesn't behave consistently:
            // i.e. leaving open tasks after finishing process instance.
            List<String> status = Arrays.asList(Status.Ready.toString());
            List<TaskSummary> taskList = taskClient.findTasksByStatusByProcessInstanceId(processInstanceId, status, 0, 10);

            assertEquals(1, taskList.size());
            TaskSummary taskSummary = taskList.get(0);
            taskClient.startTask(CONTAINER_ID, taskSummary.getId(), USER_YODA);
            taskClient.completeTask(CONTAINER_ID, taskSummary.getId(), USER_YODA, null);

            TaskInstance userTask = taskClient.findTaskById(taskSummary.getId());
            assertNotNull(userTask);
            assertEquals("Evaluate items?", userTask.getName());
            assertEquals(Status.Completed.toString(), userTask.getStatus());

            List<WorkItemInstance> workItems = processClient.getWorkItemByProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(workItems);
            assertEquals(1, workItems.size());

            WorkItemInstance workItemInstance = workItems.get(0);
            assertNotNull(workItemInstance);

            processClient.completeWorkItem(CONTAINER_ID, processInstanceId, workItemInstance.getId(), parameters);

            ProcessInstance processInstance = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(processInstance);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, processInstance.getState().intValue());

        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            throw e;
        } finally {
            changeUser(TestConfig.getUsername());
        }
    }

    @Test
    public void testStartCheckProcessWithCorrelationKey() throws Exception {
        String firstSimpleKey = "first-simple-key";
        String secondSimpleKey = "second-simple-key";
        String stringVarName = "stringData";
        String stringVarValue = "string variable test";

        CorrelationKeyFactory correlationKeyFactory = KieInternalServices.Factory.get().newCorrelationKeyFactory();

        CorrelationKey firstKey = correlationKeyFactory.newCorrelationKey(firstSimpleKey);
        CorrelationKey secondKey = correlationKeyFactory.newCorrelationKey(secondSimpleKey);

        Long firstProcessInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, firstKey);
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put(stringVarName, stringVarValue);
        Long secondProcessInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_EVALUATION, secondKey, parameters);
        try {
            ProcessInstance instance = processClient.getProcessInstance(CONTAINER_ID, firstProcessInstanceId);
            ProcessInstance expected = createEvaluationProcessInstace(firstProcessInstanceId);
            assertProcessInstance(expected, instance);
            assertEquals(firstSimpleKey, instance.getCorrelationKey());

            instance = processClient.getProcessInstance(CONTAINER_ID, secondProcessInstanceId, true);
            expected = createEvaluationProcessInstace(secondProcessInstanceId);
            assertProcessInstance(expected, instance);
            assertEquals(secondSimpleKey, instance.getCorrelationKey());
            assertTrue(instance.getVariables().containsKey(stringVarName));
            assertEquals(stringVarValue, instance.getVariables().get(stringVarName));
        } finally {
            processClient.abortProcessInstance(CONTAINER_ID, firstProcessInstanceId);
            processClient.abortProcessInstance(CONTAINER_ID, secondProcessInstanceId);
        }
    }

    @Test
    public void testStartProcessWithCustomTask() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("id", "custom id");
        Long processInstanceId = null;
        try {
            processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_CUSTOM_TASK);

            assertNotNull(processInstanceId);
            assertTrue(processInstanceId.longValue() > 0);

            ProcessInstance pi = queryClient.findProcessInstanceById(processInstanceId);
            assertNotNull(pi);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, pi.getState().intValue());

        } catch(Exception e) {
            if (processInstanceId != null) {
                processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            }
            fail("Exception " + e.getMessage());
        }
    }

    @Test
    public void testSignalContainer() throws Exception {
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_SIGNAL_PROCESS);

        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            checkAvailableSignals(CONTAINER_ID, processInstanceId);

            Object person = createPersonInstance(USER_JOHN);
            processClient.signal(CONTAINER_ID, "Signal1", person);

            processClient.signal(CONTAINER_ID, "Signal2", "My custom string event");

            ProcessInstance pi = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(pi);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, pi.getState().intValue());
        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    @Test
    public void testSignalStartProcess() throws Exception {
        try {

            List<Integer> status = new ArrayList<Integer>();
            status.add(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE);
            status.add(org.kie.api.runtime.process.ProcessInstance.STATE_ABORTED);
            status.add(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED);

            List<ProcessInstance> processInstances = queryClient.findProcessInstancesByProcessId(PROCESS_ID_SIGNAL_START, status, 0, 10);
            int initial = processInstances.size();

            Object person = createPersonInstance(USER_JOHN);
            processClient.signal(CONTAINER_ID, "start-process", person);

            processInstances = queryClient.findProcessInstancesByProcessId(PROCESS_ID_SIGNAL_START, status, 0, 10);
            assertNotNull(processInstances);
            assertEquals(initial + 1, processInstances.size());

        } catch (Exception e){
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    @Test(timeout = 60 * 1000)
    public void testStartProcessInstanceWithAsyncNodes() throws Exception {
        List<String> status = new ArrayList<String>();
        status.add(STATUS.QUEUED.toString());
        status.add(STATUS.RUNNING.toString());
        status.add(STATUS.DONE.toString());
        int originalJobCount = jobServicesClient.getRequestsByStatus(status, 0, 1000).size();

        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_ASYNC_SCRIPT);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {

            // async node is executed as a job
            List<RequestInfoInstance> jobs = jobServicesClient.getRequestsByStatus(status, 0, 1000);
            assertNotNull(jobs);
            assertEquals(originalJobCount + 1, jobs.size());

            // wait for process instance to be completed
            KieServerSynchronization.waitForProcessInstanceToFinish(processClient, CONTAINER_ID, processInstanceId);

            ProcessInstance pi = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(pi);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, pi.getState().intValue());

        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            fail(e.getMessage());
        }
    }

    @Test(timeout = 60 * 1000)
    public void testProcessInstanceWithTimer() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("timer", "1s");
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID_TIMER, parameters);
        assertNotNull(processInstanceId);
        assertTrue(processInstanceId.longValue() > 0);

        try {
            KieServerSynchronization.waitForProcessInstanceToFinish(processClient, CONTAINER_ID, processInstanceId);

            ProcessInstance pi = processClient.getProcessInstance(CONTAINER_ID, processInstanceId);
            assertNotNull(pi);
            assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, pi.getState().intValue());

        } catch (Exception e){
            processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);
            fail(e.getMessage());
        }
    }

    private ProcessInstance createSignalProcessInstance(Long processInstanceId) {
        return ProcessInstance.builder()
                .id(processInstanceId)
                .state(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE)
                .processId(PROCESS_ID_SIGNAL_PROCESS)
                .processName("signalprocess")
                .processVersion("1.0")
                .containerId(CONTAINER_ID)
                .processInstanceDescription("signalprocess")
                .initiator(TestConfig.getUsername())
                .parentInstanceId(-1l)
                .build();
    }

    private ProcessInstance createEvaluationProcessInstace(Long proccesInstanceId) {
        return ProcessInstance.builder()
                .id(proccesInstanceId)
                .state(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE)
                .processId(PROCESS_ID_EVALUATION)
                .processName("evaluation")
                .processVersion("1.0")
                .initiator(USER_YODA)
                .containerId(CONTAINER_ID)
                .processInstanceDescription("evaluation")
                .parentInstanceId(-1l)
                .build();
    }

    private void assertProcessInstance(ProcessInstance expected, ProcessInstance actual) {
        assertNotNull(actual);
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getState(), actual.getState());
        assertEquals(expected.getProcessId(), actual.getProcessId());
        assertEquals(expected.getProcessName(), actual.getProcessName());
        assertEquals(expected.getProcessVersion(), actual.getProcessVersion());
        assertEquals(expected.getContainerId(), actual.getContainerId());
        assertEquals(expected.getProcessInstanceDescription(), actual.getProcessInstanceDescription());
        assertEquals(expected.getInitiator(), actual.getInitiator());
        assertEquals(expected.getParentId(), actual.getParentId());
        assertNotNull(actual.getCorrelationKey());
        assertNotNull(actual.getDate());
    }

    private void checkAvailableSignals(String containerId, Long processInstanceId) {
        List<String> availableSignals = processClient.getAvailableSignals(containerId, processInstanceId);
        assertNotNull(availableSignals);
        assertEquals(2, availableSignals.size());
        assertTrue(availableSignals.contains("Signal1"));
        assertTrue(availableSignals.contains("Signal2"));
    }
}
