package com.fillipe.webmapper;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SalePolicyTest {
    private JSONObject verified(){
        JSONObject p=new JSONObject();
        try {p.put("id","creature-123");p.put("identitySource","data-creature");p.put("species","ekans");p.put("speciesKey","023");
            p.put("stars",2);p.put("quality","bom");p.put("rarity","common");p.put("shiny",false);p.put("locked",false);
            p.put("favorite",false);p.put("event",false);p.put("special",false);p.put("listed",false);p.put("inTeam",false);p.put("savedSpecies",false);
            p.put("iv",81);p.put("ivs",new int[]{10,11,12,13,14,21});p.put("detailVerified",true);p.put("crossSurfaceIdentity",true);}
        catch(Exception e){throw new AssertionError(e);}return p;
    }
    private JSONObject evaluate(JSONObject p,boolean consistent,boolean complete,boolean teams,int count,int rank){return SalePolicy.evaluate(p,consistent,complete,teams,count,rank,135,3,"Scyther,Scizor");}
    @Test public void onlyFullyVerifiedWeakDuplicateCanBecomeCandidate(){assertEquals("SELL_CANDIDATE",evaluate(verified(),true,true,true,4,4).optString("action"));}
    @Test public void unverifiedSavedTeamsForceReview(){assertEquals("REVIEW",evaluate(verified(),true,true,false,4,4).optString("action"));}
    @Test public void unknownStateCannotBecomeNegativeEvidence(){JSONObject p=verified();p.remove("event");assertEquals("REVIEW",evaluate(p,true,true,true,4,4).optString("action"));}
    @Test public void disagreementCannotBecomeCandidate(){assertEquals("REVIEW",evaluate(verified(),false,true,true,4,4).optString("action"));}
    @Test public void excellentQualityIsProtected(){JSONObject p=verified();try{p.put("quality","Excelente");}catch(Exception e){throw new AssertionError(e);}assertEquals("PROTECT",evaluate(p,true,true,true,4,4).optString("action"));}
    @Test public void fourStarsAreProtected(){JSONObject p=verified();try{p.put("stars",4);p.put("quality","excelente");}catch(Exception e){throw new AssertionError(e);}assertEquals("PROTECT",evaluate(p,true,true,true,4,4).optString("action"));}
    @Test public void topThreeAndUniqueAreProtected(){assertEquals("PROTECT",evaluate(verified(),true,true,true,4,3).optString("action"));assertEquals("PROTECT",evaluate(verified(),true,true,true,1,1).optString("action"));}
    @Test public void unknownJessieIdentityForcesReview(){JSONObject p=verified();try{p.put("crossSurfaceIdentity",false);}catch(Exception e){throw new AssertionError(e);}assertEquals("REVIEW",evaluate(p,true,true,true,4,4).optString("action"));}
}
