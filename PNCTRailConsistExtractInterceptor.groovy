package PNCT

import com.navis.argo.ContextHelper
import com.navis.argo.EdiFacility
import com.navis.argo.EdiRailCar
import com.navis.argo.EdiRailCarVisit
import com.navis.argo.EdiTrainVisit
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.portal.EdiExtractDao
import com.navis.argo.business.reference.CarrierItinerary
import com.navis.argo.business.reference.CarrierService
import com.navis.argo.business.reference.PointCall
import com.navis.edi.EdiPropertyKeys
import com.navis.external.edi.entity.AbstractEdiExtractInterceptor
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.ValueObject
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.rail.business.entity.RailcarVisit
import com.navis.rail.business.entity.TrainVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Element

/**
 * <Purpose>
 *
 * Author: <a href="mailto:mpraveen@weservetech.com">
 M Praveen Babu</a>
 * Date: 4/1/2019 : 6:39 PM
 * JIRA: <Specify the JIRA tracking number>
 * Called from: <Specify from where this groovy is called>
 */
class PNCTRailConsistExtractInterceptor extends AbstractEdiExtractInterceptor {

    /* @Override
     Element beforeEdiMap(Map inParams) {
         LOGGER.setLevel(Level.DEBUG)
         LOGGER.debug("Inside the Before EDi MAP :: ")
         LOGGER.debug("Inside the Before EDi MAP In Params :: $inParams")
         LOGGER.debug("Inside the before EDI Map :: "+inParams.get("ERROR"))
         if(inParams.get("ERROR") == null){
             inParams.put("ERROR", EdiPropertyKeys.EXTRACT_INITIALIZE_FAILED)
         }else{
             registerError("There is no way forward :: ")
             ContextHelper.getThreadEdiPostingContext().throwIfAnyViolations()
         }
         LOGGER.debug("Inside the before EDI Map :: "+inParams.get("ERROR"))

     }*/

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

    /*    @Override
        Element beforeEdiMap(Map inParams) {
            LOGGER.setLevel(Level.DEBUG)
            LOGGER.debug("Inside the PNCTRailConsistExtractInterceptor :: Start ")
            Element railConsistTransaction = (Element) inParams.get(XML_TRANSACTION);
            LOGGER.debug("Entity :: " + inParams.get("ENTITY"))
            List<String> portOfRotation = new ArrayList<String>()
            Map<String, String> railCarSequenceMap = new HashMap<String, String>();
            Element ediFacility = railConsistTransaction.getChild("facility", railConsistTransaction.getNamespace());
            LOGGER.debug("Facility :: " + ediFacility.getFacilityId())

            EdiTrainVisit ediTrainVisit = (EdiTrainVisit) railConsistTransaction.getChild("ediTrainVisit", railConsistTransaction.getNamespace());
            if (ediTrainVisit != null) {
                String ediTrainId = ediTrainVisit.getTrainId()
                LOGGER.debug("Train Id :: $ediTrainId")
                Facility facility = Facility.findFacility(ediFacility.getFacilityId())
                CarrierVisit carrierVisit = CarrierVisit.findCarrierVisit(facility, LocTypeEnum.TRAIN, ediTrainId);
                TrainVisitDetails trainVisitDetails = TrainVisitDetails.resolveTvdFromCv(carrierVisit)
                LOGGER.debug("Train visit Details :: $trainVisitDetails")
                CarrierService carrierService = trainVisitDetails.getCvdService();
                LOGGER.debug("Carrier Service :: $carrierService")
                if (carrierService.getSrvcItinVao() != null && carrierService.getSrvcItinVao().getEntityPrimaryKey() != null) {
                    CarrierItinerary carrierItinerary = CarrierItinerary.hydrate(carrierService.getSrvcItinVao().getEntityPrimaryKey());
                    if (carrierItinerary != null) {
                        if (carrierItinerary.getItinPoints() != null && carrierItinerary.getItinPoints().size() > 0) {
                            for (PointCall pointCall : carrierItinerary.getItinPoints()) {
                                if (pointCall.getCallPoint() != null) {
                                    portOfRotation.add(pointCall.getCallPoint().getPointId())
                                }
                            }
                        }
                    }
                }
            }
            LOGGER.debug("Current Port of Rotation :: $portOfRotation")
            List<Element> railCarVisitList = railConsistTransaction.getChildren("ediRailCarVisit", railConsistTransaction.getNamespace())
            if (railCarVisitList != null && railCarVisitList.size() > 0) {
                Iterator railCarVisitIterator = railCarVisitList.iterator();

                while (railCarVisitIterator.hasNext()) {
                    Element ediRailCarVisitElement = railCarVisitIterator.next()
                    Element railCarVisitRoutingElement = ediRailCarVisitElement.getChild("railCarRouting", railConsistTransaction.getNamespace())
                    if (railCarVisitRoutingElement != null) {
                        Element dischargePortElement = railCarVisitRoutingElement.getChild("dischargePort1", railConsistTransaction.getNamespace())
                        if (dischargePortElement != null) {
                            EdiRailCarVisit ediRailCarVisit = (EdiRailCarVisit) ediRailCarVisitElement
                            railCarSequenceMap.put(ediRailCarVisit.getRailCarSequence(), dischargePortElement.getAttributeValue("portId"))
                        }
                    }
                }
            }
            LOGGER.debug("Rail Car Sequence Map :: $railCarSequenceMap")
            LOGGER.debug("Inside the PNCTRailConsistExtractInterceptor :: Start ")
        }*/
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
    private static final String XML_TRANSACTION = "XML_TRANSACTION";
    private static final Logger LOGGER = Logger.getLogger(PNCTRailConsistExtractInterceptor.class)
}
