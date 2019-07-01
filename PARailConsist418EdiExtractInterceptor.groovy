import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.CarrierItinerary
import com.navis.argo.business.reference.CarrierService
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.portal.EdiExtractDao
import com.navis.argo.business.reference.CarrierItinerary
import com.navis.argo.business.reference.PointCall
import com.navis.argo.business.reference.UnLocCode
import com.navis.external.edi.entity.AbstractEdiExtractInterceptor
import com.navis.framework.business.atoms.PredicateVerbEnum
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.portal.UserContext
import com.navis.rail.business.entity.RailcarVisit
import com.navis.rail.business.entity.TrainVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace

/**
 * Copyright 2018 Ports America.  All Rights Reserved.  This code contains the CONFIDENTIAL and PROPRIETARY information of Ports America.
 **/

/**
 * Version #: 4.0.134.0
 * Author: Jimmy Palanca
 * Work Item #: 210031
 * Called From: CSX rail consist 418 edi session
 * Description: make sure port names are upper-case and add state code(s) where needed
 * PLEASE UPDATE THE BELOW HISTORY WHENEVER THE GROOVY IS MODIFIED
 * History:
 *{Date}:{Author}:{WorkItem#}:{short issue/solution description}
 * 04/15/2019:Jimmy : 210031: add state code(s) and make port names uppercase
 * **/

class PARailConsist418EdiExtractInterceptor extends AbstractEdiExtractInterceptor {
    private static Logger LOGGER = Logger.getLogger(PARailConsist418EdiExtractInterceptor.class)

    @Override
    Element beforeEdiMap(Map inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("PARailConsist418EdiExtractInterceptor beforeEdiMap started")

        if (inParams == null) {
            LOGGER.warn("Null Parameters received !");
            return super.beforeEdiExtract(inParams);
        }

        Element transaction = (Element) inParams.get("XML_TRANSACTION")
        LOGGER.info("transaction"+transaction)
        Namespace ns = transaction.getNamespace()

        List<Element> trainVisitElement=transaction.getChildren("facility",ns)
        LOGGER.info("trainVisitElement"+trainVisitElement)
        /*Element element=new Element("rcarvFlexString02",trainVisitElement.getName())
        LOGGER.info("element"+trainVisitElement)*/

        List<Element> carVisits = transaction.getChildren("ediRailCarVisit", ns)
        LOGGER.info("carVisits"+carVisits)
        if ((carVisits != null) && (carVisits.size() > 0)){
            for (int i = 0 ; i < carVisits.size() ; i++){

                Element carRouting = carVisits[i].getChild("railCarRouting", ns)
                if (carRouting != null)
                    processPorts(carRouting, ns)
                else
                    LOGGER.warn("Railcar routing not found!")
            }
        }
        else
            LOGGER.warn("No railcar visits found!")

        // process containers
        List<Element> containers = transaction.getChildren("ediRailCarContainer", ns)
        if ((containers != null) && (containers.size() > 0)){
            for (int i = 0 ; i < containers.size() ; i++){
                Element container = containers[i].getChild("ediContainer", ns)
                if (container != null)
                    processPorts(container, ns)
                else
                    LOGGER.warn("Container not found!")
            }
        }
        else
            LOGGER.warn("No railcar containers found!");

        LOGGER.info("PARailConsist418EdiExtractInterceptor beforeEdiMap completed")

        return transaction
    }


    @Override
    void beforeEdiExtract(Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside before EDI extract :: " + inParams)
        EdiExtractDao inEdiExtractDAO = (EdiExtractDao) inParams.get("DAO");
        List<String> portOfRotation = new ArrayList<String>()
        Map<String, String> railCarSequenceMap = new HashMap<String, String>();

        CarrierVisit carrierVisit = CarrierVisit.hydrate(inEdiExtractDAO.getCvGkey())
        if (carrierVisit != null) {
            TrainVisitDetails trainVisitDetails = TrainVisitDetails.resolveTvdFromCv(carrierVisit)
            LOGGER.debug("Train Visit Details :: $trainVisitDetails")

            Set<RailcarVisit> railcarVisitSet = trainVisitDetails.getRvdtlsOutboundRailcarVisits()
            LOGGER.info("railcarVisitSet::"+railcarVisitSet)
            if (railcarVisitSet != null && railcarVisitSet.size() > 0) {
                for (RailcarVisit railcarVisit : railcarVisitSet) {
                    LOGGER.info("railcarVisit::"+railcarVisit)
                    String rcarvDestination=railcarVisit.getRcarvDestination()
                    LOGGER.info("rcarvDestination::"+rcarvDestination)
                    String pointId=railcarVisit.getRcarvDischargePoint().getPointId()
                    LOGGER.info("pointId"+pointId)
                    //railCarSequenceMap.put(String.valueOf(railcarVisit.getRcarvOutSeq()), railcarVisit.getRcarvDischargePoint().getPointId())
                    railCarSequenceMap.put(String.valueOf(railcarVisit.getRcarvOutSeq()), rcarvDestination)
                }
            }

            // if there is only 1 rail car, need not have the logic to check for the port rotation.
            if (railCarSequenceMap.size() > 1) {
                CarrierService carrierService = trainVisitDetails.getCvdService();
                LOGGER.debug("Carrier Service :: $carrierService")
                if (carrierService != null && carrierService.getSrvcItinVao() != null && carrierService.getSrvcItinVao().getEntityPrimaryKey() != null) {
                    CarrierItinerary carrierItinerary = CarrierItinerary.hydrate(carrierService.getSrvcItinVao().getEntityPrimaryKey());
                    LOGGER.info("carrierItinerary::"+carrierItinerary)
                    if (carrierItinerary != null) {
                        if (carrierItinerary.getItinPoints() != null && carrierItinerary.getItinPoints().size() > 0) {
                            for (PointCall pointCall : carrierItinerary.getItinPoints()) {
                                LOGGER.info("pointCall::"+pointCall)
                                if (pointCall.getCallPoint() != null) {
                                    portOfRotation.add(pointCall.getCallPoint().getPointId())
                                }
                            }
                        }
                    }
                }
            }
        }

        if (portOfRotation.size() > 0 && railCarSequenceMap.size() > 0) {
            Map<String, Integer> sortedPortIdMap = new LinkedHashMap<String, Integer>();

            for (String currentValue : railCarSequenceMap.values()) {
                LOGGER.info("currentValue::"+currentValue)
                LOGGER.info("portOfRotation.indexOf(currentValue) value ::"+portOfRotation.indexOf(currentValue))

                if (portOfRotation.indexOf(currentValue) != -1  ) {
                    sortedPortIdMap.put(currentValue, portOfRotation.indexOf(currentValue))
                    //linkedHashMap.put()
                }
            }
            LOGGER.info("sortedPortIdMap values::"+sortedPortIdMap)
            Object[] currentValues = sortedPortIdMap.values().toArray();
            LOGGER.info("currentValues::"+currentValues)
            boolean canContinue = true;
            for (int i = 0; i < currentValues.length && canContinue; i++) {
                System.out.println(currentValues[i]);
                for (int j = i + 1; j < currentValues.length; j++) {
                    LOGGER.info("(Integer) currentValues[i] values"+(Integer) currentValues[i] )
                    LOGGER.info("(Integer) currentValues[j] values"+(Integer) currentValues[j])
                    if ((Integer) currentValues[i] > (Integer) currentValues[j]) {
                        LOGGER.info("coming inside the current values  if condition !!!!!!!!!!!hence returning")
                        System.out.println(currentValues[i] + " is greater than " + currentValues[j]);
                        MetafieldId ufvFlexString02MetafieldId = MetafieldIdFactory.valueOf("unitId");
                        addFilter(ufvFlexString02MetafieldId, PredicateVerbEnum.EQ,null);
                        sendMail(SUBJECT, MSG_BODY)
                        /*registerError("The port of rotation sequence is not mathcing with the rail car sequence.")
                        getMessageCollector().appendMessage(MessageLevel.SEVERE, EdiPropertyKeys.EXTRACT_INITIALIZE_FAILED, null, null)
                        inParams.put("ERROR", EdiPropertyKeys.EXTRACT_INITIALIZE_FAILED)
                        //throw BizFailure.create(EdiPropertyKeys.EXTRACT_INITIALIZE_FAILED, null);
                        throw new BizViolation(EdiPropertyKeys.EXTRACT_INITIALIZE_FAILED, null, null, null)
                        canContinue = false;*/
                        break;
                    }
                }
            }
        }


        LOGGER.debug(" Current Port of Rotation ::  $portOfRotation ")
        LOGGER.debug(" Rail Car Vists :: $railCarSequenceMap")
    }



    private void processPorts(Element parent, Namespace ns){
        if (parent != null){
            processPort(parent, "loadPort", ns)
            processPort(parent, "dischargePort1", ns)
            processPort(parent, "dischargePort2", ns)
        }
    }

    private void processPort(Element parent, String portType, Namespace ns){
        Element port = parent.getChild(portType, ns)
        if (port != null){
            UnLocCode unloc = getUnLocCode(port, ns)
            String portName = getAttribute(port, "portName", ns)
            String state = (unloc?.unlocSubDiv != null ? unloc.unlocSubDiv.toUpperCase() : "")

            if (((portName == null) || (portName == "")) && (unloc != null))
                portName = unloc.unlocPlaceName

            if ((portName != null) && (portName != ""))
                port.setAttribute("portName", portName.toUpperCase() + (state != "" ? ",${state}" : ""), ns)
        }
    }

    private String getAttribute(Element element, String attribName, Namespace ns){
        Attribute attrib = element.getAttribute(attribName, ns)
        if (attrib != null)
            return attrib.value
        else {
            LOGGER.warn("Attribute ${attribName} not found in ${element.name}")
        }
    }

    private UnLocCode getUnLocCode(Element port, Namespace ns){
        Element portCodes = port.getChild("portCodes", ns)
        if (portCodes != null){
            String unLocCode = getAttribute(portCodes, "unLocCode", ns)
            if ((unLocCode != null) && (unLocCode != ""))
                return UnLocCode.findUnLocCode(unLocCode)
            else
                LOGGER.warn("UN location code for ${port.name} not found!")
        }
        else
            LOGGER.warn("Port codes for ${port.name} not found!")
        return null
    }

    private static  void sendMail(String subject, String msgBody) {
        try {
            GeneralReference genRef = GeneralReference.findUniqueEntryById("RAIL_COSIST", "EMAIL");
            LOGGER.debug("genRef::"+genRef)
            if (genRef != null) {
                String fromAddress = (genRef != null) ? genRef.getRefValue1() : null;
                String toAddress = (genRef != null) ? genRef.getRefValue2() : null;

                ESBClientHelper.sendEmailAttachments(UserContext.getThreadUserContext(), null, toAddress, fromAddress, subject, msgBody, null)
            }

        } catch (Exception e) {
            LOGGER.debug("Exception while sending mail :: " + e.getMessage())
        }
    }
    private static final String SUBJECT = "Port rotation validation"
    private static final String MSG_BODY = "The port of rotation sequence is not mathcing with the rail car sequence"
}
