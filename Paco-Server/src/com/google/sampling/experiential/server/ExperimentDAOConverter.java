package com.google.sampling.experiential.server;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.sampling.experiential.dao.CSDataTypeDao;
import com.google.sampling.experiential.dao.CSGroupTypeInputMappingDao;
import com.google.sampling.experiential.dao.CSUserDao;
import com.google.sampling.experiential.dao.dataaccess.Choice;
import com.google.sampling.experiential.dao.dataaccess.ChoiceCollection;
import com.google.sampling.experiential.dao.dataaccess.DataType;
import com.google.sampling.experiential.dao.dataaccess.Experiment;
import com.google.sampling.experiential.dao.dataaccess.ExperimentVersionMapping;
import com.google.sampling.experiential.dao.dataaccess.ExternStringInput;
import com.google.sampling.experiential.dao.dataaccess.ExternStringListLabel;
import com.google.sampling.experiential.dao.dataaccess.Group;
import com.google.sampling.experiential.dao.dataaccess.InformedConsent;
import com.google.sampling.experiential.dao.dataaccess.Input;
import com.google.sampling.experiential.dao.dataaccess.InputCollection;
import com.google.sampling.experiential.dao.dataaccess.InputOrderAndChoice;
import com.google.sampling.experiential.dao.dataaccess.User;
import com.google.sampling.experiential.dao.impl.CSDataTypeDaoImpl;
import com.google.sampling.experiential.dao.impl.CSGroupTypeInputMappingDaoImpl;
import com.google.sampling.experiential.dao.impl.CSUserDaoImpl;
import com.pacoapp.paco.shared.model2.ExperimentDAO;
import com.pacoapp.paco.shared.model2.ExperimentGroup;
import com.pacoapp.paco.shared.model2.Feedback;
import com.pacoapp.paco.shared.model2.GroupTypeEnum;
import com.pacoapp.paco.shared.model2.Input2;
import com.pacoapp.paco.shared.util.ErrorMessages;

public class ExperimentDAOConverter {
  DateTimeFormatter formatter = DateTimeFormat.forPattern("YYYY/MM/dd");
  public static final Logger log = Logger.getLogger(ExperimentDAOConverter.class.getName());
  
  public Experiment convertToExperiment(ExperimentDAO experimentDao) throws SQLException {
    InformedConsent newInformedConsent = null;
    if (experimentDao.getInformedConsentForm() != null && !experimentDao.getInformedConsentForm().equals("")) {
      newInformedConsent = new InformedConsent();
      newInformedConsent.setInformedConsent(experimentDao.getInformedConsentForm());
    }
    Experiment experiment = new Experiment();
    experiment.setTitle(experimentDao.getTitle());
    experiment.setDescription(experimentDao.getDescription());
    // create option should always be false. we should have the user authenticated by the time we hit save button.
    experiment.setCreator(convertToUserAndCreate(experimentDao.getCreator(), false));
    experiment.setOrganization(experimentDao.getOrganization());
    experiment.setContactEmail(experimentDao.getContactEmail());
    experiment.setInformedConsent(newInformedConsent);
    experiment.setDeleted(experimentDao.getDeleted());
    experiment.setModifiedDate(experimentDao.getModifyDate() != null ? formatter.parseDateTime(experimentDao.getModifyDate()) : null);
    experiment.setPublished(experimentDao.getPublished());
    experiment.setRingtoneUri(experimentDao.getRingtoneUri());
    experiment.setPostInstallInstructions(experimentDao.getPostInstallInstructions());
    
    return experiment; 
  }
  
  public List<Group> convertToGroup(List<ExperimentGroup> experimentGroups) {
    List<Group> groups = Lists.newArrayList();
    Group group = null;
    for ( ExperimentGroup experimentGroup : experimentGroups) {
      group = new Group();
      group.setName(experimentGroup.getName());
      if (experimentGroup.getGroupType() != null) {
        group.setGroupTypeId(experimentGroup.getGroupType().getGroupTypeId());
      } 
      group.setCustomRendering(experimentGroup.getCustomRenderingCode());
      group.setFixedDuration(experimentGroup.getFixedDuration());
      group.setStartDate(experimentGroup.getStartDate() != null ? formatter.parseDateTime(experimentGroup.getStartDate()) : null);
      group.setEndDate(experimentGroup.getEndDate() != null ? formatter.parseDateTime(experimentGroup.getEndDate()) : null);
      group.setRawDataAccess(experimentGroup.getRawDataAccess());
      group.setEndOfDayGroup(experimentGroup.getEndOfDayReferredGroupName());
      group.setGroupTypeId(experimentGroup.getGroupType().getGroupTypeId());
      groups.add(group);
    }  
    return groups; 
  }
  
  public InputCollection convertToInputCollection(ExperimentGroup experimentGroup) throws SQLException {
    InputCollection inputCollection = null;
    Input inputObj = null;
    InputOrderAndChoice inputOrderAndChoiceObj = null;
    Input2 input2Obj = null;
    Map<String, InputOrderAndChoice> inputOrderAndChoices = null;
    if (experimentGroup.getInputs().size() > 0) {
      inputOrderAndChoices = Maps.newHashMap();
      inputCollection = new InputCollection();
      for (int order=0; order<experimentGroup.getInputs().size(); order++) {
        input2Obj = experimentGroup.getInputs().get(order);
        inputObj = convertToInput(input2Obj);
        inputOrderAndChoiceObj = convertInputToInputOrderAndChoice(inputObj, order+1, input2Obj.getListChoices());
        inputOrderAndChoices.put(inputObj.getName().getLabel(), inputOrderAndChoiceObj);
      }
      inputCollection.setInputOrderAndChoices(inputOrderAndChoices);
    }
    return inputCollection; 
  }
  
  
  
  private InputOrderAndChoice convertInputToInputOrderAndChoice(Input inputObj, Integer order, List<String> choices) throws SQLException {
    InputOrderAndChoice inputOrderAndChoiceObj = null;
    ChoiceCollection choiceCollectionObj = null;
    inputOrderAndChoiceObj = new InputOrderAndChoice();
    inputOrderAndChoiceObj.setInput(inputObj);
    inputOrderAndChoiceObj.setInputOrder(order);
    choiceCollectionObj = convertToChoiceCollectionAndCreate(choices);
    inputOrderAndChoiceObj.setChoiceCollection(choiceCollectionObj);
    return inputOrderAndChoiceObj;
  }
  
  public User convertToUserAndCreate(String email, Boolean createOption) throws SQLException {
    CSUserDao userDao = new CSUserDaoImpl();
    User user = new User();
    user.setUserId(userDao.getUserAndCreate(email, createOption).getUserId());
    user.setWho(email);
    return user; 
  }
  
  public ChoiceCollection convertToChoiceCollectionAndCreate(List<String> choices) throws SQLException {
    ChoiceCollection choiceCollection = null;
    Map<String, Choice> choiceOrderMap = Maps.newHashMap();
    
    if ( choices != null && choices.size() >0) {
      choiceCollection = new ChoiceCollection();
      ExternStringListLabel label = null;
      String currentChoice = null;
      Choice choice = null;
      for (int order=0; order < choices.size(); order++) {
        currentChoice = choices.get(order);
        choice = new Choice();
        label = new ExternStringListLabel();
        label.setExternStringListLabelId(null);
        label.setLabel(currentChoice);
        choice.setChoiceLabel(label);
        choice.setChoiceOrder(order+1);
        choiceOrderMap.put(currentChoice, choice);
      }
      choiceCollection.setChoices(choiceOrderMap);
    }
    return choiceCollection;
  }
  
  public Input convertToInput(Input2 input2Obj) throws SQLException {
    Input inputObj = new Input();
    CSDataTypeDao dataTypeDao = new CSDataTypeDaoImpl();
    List<DataType> allDataTypes = dataTypeDao.getAllDataTypes();
    ExternStringInput variableName = new ExternStringInput();
    ExternStringInput variableText = new ExternStringInput();
    DataType responseDataType = dataTypeDao.getMatchingDataType(allDataTypes, input2Obj.getResponseType(), input2Obj.isNumeric(), input2Obj.getMultiselect());
    variableName.setLabel(input2Obj.getName());
    variableText.setLabel(input2Obj.getText());
    
    inputObj.setName(variableName);
    inputObj.setRequired(input2Obj.getRequired());
    inputObj.setConditional(input2Obj.getConditionExpression());
    inputObj.setResponseDataType(responseDataType);
    inputObj.setText(variableText);
    inputObj.setLikertSteps(input2Obj.getLikertSteps()==null ? 0 : input2Obj.getLikertSteps());
    inputObj.setLeftLabel(input2Obj.getLeftSideLabel());
    inputObj.setRightLabel(input2Obj.getRightSideLabel());
    return inputObj;
  }
  
  public List<Input2> convertToInput2(List<Input> inputLst) {
    List<Input2> input2List = Lists.newArrayList();
    for (Input eachInput : inputLst) {
      input2List.add(convertToInput2(eachInput));
    }
    return input2List;
  }
  
  public Input2 convertToInput2(Input inputObj) {
    Input2 input2Obj = new Input2();
    input2Obj.setName(inputObj.getText().getLabel());
    input2Obj.setRequired(inputObj.isRequired());
    input2Obj.setConditionExpression(inputObj.getConditional());
    if (inputObj.getConditional() != null) {
      input2Obj.setConditional(true);
    }
    input2Obj.setResponseType(inputObj.getResponseDataType().getName());
    input2Obj.setText(inputObj.getText().getLabel());
    input2Obj.setLikertSteps(inputObj.getLikertSteps());
    input2Obj.setLeftSideLabel(inputObj.getLeftLabel());
    input2Obj.setRightSideLabel(inputObj.getRightLabel());
    return input2Obj;
  }
  
  public List<ExperimentVersionMapping> convertToExperimentVersionMapping(ExperimentDAO experimentDao) throws SQLException {
    List<ExperimentVersionMapping> newMappingList = Lists.newArrayList();
    ExperimentVersionMapping currentMappingObj = null;
    List<Group> convertedGroups = convertToGroup(experimentDao.getGroups());
    for (Group group : convertedGroups) {
      currentMappingObj = new ExperimentVersionMapping();
      currentMappingObj.setExperimentInfo(convertToExperiment(experimentDao));
      currentMappingObj.setExperimentId(experimentDao.getId());
      currentMappingObj.setExperimentVersion(experimentDao.getVersion());
      currentMappingObj.setGroupInfo(group);
      currentMappingObj.setInputCollection(convertToInputCollection(experimentDao.getGroupByName(group.getName())));
      newMappingList.add(currentMappingObj);
    }
    
    return newMappingList;
    
  }
  
  public ExperimentGroup createPredefinedExperimentGroupForGroupType(GroupTypeEnum groupType, Boolean recordPhoneDetails) throws SQLException {
    CSGroupTypeInputMappingDao gtimDaoImpl = new CSGroupTypeInputMappingDaoImpl();
    ExperimentGroup predefinedGrp = new ExperimentGroup();
    ExperimentDAOConverter daoConverter = new ExperimentDAOConverter();
    String lowerCaseGroupTypeName = groupType.name().toLowerCase();
    List<Input> predefinedInputOrigLst = gtimDaoImpl.getAllFeatureInputs().get(lowerCaseGroupTypeName);
    List<Input> predefinedInputModifiedLst = null;
    
    if (!recordPhoneDetails && GroupTypeEnum.SYSTEM.equals(groupType)) {
      predefinedInputModifiedLst = Lists.newArrayList();
      for (Input i : predefinedInputOrigLst) {
        String inputLabel = i.getName().getLabel();
        // TODO better way
        if (!(inputLabel.equalsIgnoreCase("make") ||  inputLabel.equalsIgnoreCase("model") || inputLabel.equalsIgnoreCase("android") || inputLabel.equalsIgnoreCase("carrier")
                || inputLabel.equalsIgnoreCase("display"))) {
          predefinedInputModifiedLst.add(i);
        } 
      }
    } else {
      predefinedInputModifiedLst = predefinedInputOrigLst;
    }
    predefinedGrp.setName(lowerCaseGroupTypeName);
    predefinedGrp.setGroupType(groupType);
    predefinedGrp.setInputs(daoConverter.convertToInput2(predefinedInputModifiedLst));
    predefinedGrp.setFeedback(new Feedback("Thanks"));
    return predefinedGrp;
  }
  

  public void splitGroups(ExperimentDAO eachExperiment)  throws SQLException {
    List<ExperimentGroup> predefinedGroups = null;
    // add predefined system group
    ExperimentGroup systemGroup = null;
    try {
      predefinedGroups = Lists.newArrayList();
      // add predefined system group
      systemGroup = createPredefinedExperimentGroupForGroupType(GroupTypeEnum.SYSTEM, eachExperiment.getRecordPhoneDetails());
     
      // chk if splitting is neccessary
      for (ExperimentGroup eg: eachExperiment.getGroups()) {
        if (eg.getGroupType() == null)  {
          // once it saved with splitted groups, then even predefined groups will have inputs
          eg.setGroupType(GroupTypeEnum.SURVEY);
          if (eg.getAccessibilityListen() || eg.getGroupType().equals(GroupTypeEnum.ACCESSIBILITY)) {
            ExperimentGroup accListen = createPredefinedExperimentGroupForGroupType(GroupTypeEnum.ACCESSIBILITY, eachExperiment.getRecordPhoneDetails());
            accListen.setAccessibilityListen(true);
            eg.setAccessibilityListen(false);
            predefinedGroups.add(accListen);
          }
          if (eg.getLogShutdown() || eg.getGroupType().equals(GroupTypeEnum.PHONESTATUS)) {
            ExperimentGroup logPhoneActions = createPredefinedExperimentGroupForGroupType(GroupTypeEnum.PHONESTATUS, eachExperiment.getRecordPhoneDetails());
            logPhoneActions.setLogShutdown(true);
            eg.setLogShutdown(false);
            predefinedGroups.add(logPhoneActions);
          }
    
          if ( eg.getLogActions() || eg.getGroupType().equals(GroupTypeEnum.APPUSAGE))  {
            ExperimentGroup appUsage = createPredefinedExperimentGroupForGroupType(GroupTypeEnum.APPUSAGE, eachExperiment.getRecordPhoneDetails());
            appUsage.setLogActions(true);
            eg.setLogActions(false);
            predefinedGroups.add(appUsage);
          }
          if (eg.getLogNotificationEvents() || eg.getGroupType().equals(GroupTypeEnum.NOTFICATION)) {
            ExperimentGroup logNotifGrp = createPredefinedExperimentGroupForGroupType(GroupTypeEnum.NOTFICATION, eachExperiment.getRecordPhoneDetails());;
            logNotifGrp.setLogNotificationEvents(true);
            eg.setLogNotificationEvents(false);
            predefinedGroups.add(logNotifGrp);
          }
        } 
      }
      predefinedGroups.add(systemGroup);
      List<ExperimentGroup> origGroups = eachExperiment.getGroups();
      ExperimentGroup matchingGroupInDS = null;
      // add all predefined grps, only if its not already present
      for (ExperimentGroup egt : predefinedGroups) {
        matchingGroupInDS = eachExperiment.getGroupByName(egt.getName());
        if (matchingGroupInDS == null) {
          origGroups.add(egt);
        }
      }
    } catch (Exception e) {
      log.warning(ErrorMessages.GENERAL_EXCEPTION.getDescription() + ExceptionUtil.getStackTraceAsString(e));
    }
    log.info("splitting groups for an experiment finished");
  }
}
