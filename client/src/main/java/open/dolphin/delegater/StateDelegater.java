package open.dolphin.delegater;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.jersey.api.client.ClientResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import open.dolphin.client.Dolphin;
import open.dolphin.infomodel.*;
import open.dolphin.project.Project;
import open.dolphin.util.BeanUtils;

/**
 * State変化関連のデレゲータ
 * @author masuda, Masuda Naika
 */
public class StateDelegater extends BusinessDelegater {
    
    private static final String RES_CS = "stateRes/";
    private static final String SUBSCRIBE_PATH = RES_CS + "subscribe/";
    
    private static final boolean debug = false;
    private static final StateDelegater instance;
    
    private String fid;
    private String clientUUID;

    static {
        instance = new StateDelegater();
    }
    
    private StateDelegater() {
        fid = Project.getFacilityId();
        clientUUID = Dolphin.getInstance().getClientUUID();
    }
    
    public static StateDelegater getInstance() {
        return instance;
    }
    
    public int putStateMsgModel(StateMsgModel msg) {
        
        msg.setFacilityId(fid);
        msg.setIssuerUUID(clientUUID);
        
        StringBuilder sb = new StringBuilder();
        sb.append(RES_CS);
        sb.append("state");
        String path = sb.toString();

        String json = getConverter().toJson(msg);

        ClientResponse response = getResource(path, null)
                .type(MEDIATYPE_JSON_UTF8)
                .put(ClientResponse.class, json);

        int status = response.getStatus();
        String enityStr = response.getEntity(String.class);
        debug(status, enityStr);
        
        if (status != HTTP200) {
            return -1;
        }
        
        return Integer.parseInt(enityStr);
    }
    
    public StateMsgModel subscribe() {
        
        String path = SUBSCRIBE_PATH;
        
        ClientResponse response = JerseyClient.getInstance()
                .getAsyncResource(path)
                .accept(MEDIATYPE_TEXT_UTF8)
                .get(ClientResponse.class);
        
        int status = response.getStatus();
        String entityStr = response.getEntity(String.class);
        
        debug(status, entityStr);
        
        if (status != HTTP200) {
            return null;
        }
        
        StateMsgModel msg = (StateMsgModel) 
                getConverter().fromJson(entityStr, StateMsgModel.class);
        
        // PatientModelが乗っかってきている場合は保険をデコード
        PatientModel pm = msg.getPatientModel();
        if (pm != null) {
            decodeHealthInsurance(pm);
        }
        PatientVisitModel pvt = msg.getPatientVisitModel();
        if (pvt.getPatientModel() != null) {
            decodeHealthInsurance(pvt.getPatientModel());
        }
        return msg;
    }
    
    /**
     * バイナリの健康保険データをオブジェクトにデコードする。
     *
     * @param patient 患者モデル
     */
    private void decodeHealthInsurance(PatientModel patient) {

        // Health Insurance を変換をする beanXML2PVT
        Collection<HealthInsuranceModel> c = patient.getHealthInsurances();

        if (c != null && c.size() > 0) {

            List<PVTHealthInsuranceModel> list = new ArrayList<PVTHealthInsuranceModel>(c.size());

            for (HealthInsuranceModel model : c) {
                try {
                    // byte[] を XMLDecord
                    PVTHealthInsuranceModel hModel = (PVTHealthInsuranceModel) BeanUtils.xmlDecode(model.getBeanBytes());
                    list.add(hModel);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }

            patient.setPvtHealthInsurances(list);
            patient.getHealthInsurances().clear();
            patient.setHealthInsurances(null);
        }
    }

    @Override
    protected void debug(int status, String entity) {
        if (debug || DEBUG) {
            super.debug(status, entity);
        }
    }
}
