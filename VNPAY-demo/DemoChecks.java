import java.util.*;
import org.json.JSONObject;
public class DemoChecks {
    static void check(boolean b,String label) { if(!b) throw new AssertionError(label); System.out.println("PASS "+label); }
    public static void main(String[] args) throws Exception {
        if(!SeminarDemo.MOCK) throw new IllegalStateException("Tests require local mock mode");
        var p=new SeminarDemo.Payment("test01",25000);
        var m=SeminarDemo.callback(p);
        check(SeminarDemo.valid(m),"valid callback");
        m.put("vnp_Amount","1"); check(!SeminarDemo.valid(m),"tampered callback rejected");
        JSONObject r=SeminarDemo.query(p);
        check(SeminarDemo.verifyQuery(p,r),"valid query response");
        check("01".equals(r.getString("vnp_TransactionStatus")),"API success can have pending payment");
        r.put("vnp_Amount","1"); check(!SeminarDemo.verifyQuery(p,r),"tampered query rejected");
        r.put("vnp_SecureHash",SeminarDemo.hmac(SeminarDemo.queryHash(r)));
        check(!SeminarDemo.verifyQuery(p,r),"signed amount mismatch rejected");
        r=SeminarDemo.query(p); r.put("vnp_TxnRef","other"); r.put("vnp_SecureHash",SeminarDemo.hmac(SeminarDemo.queryHash(r)));
        check(!SeminarDemo.verifyQuery(p,r),"signed reference mismatch rejected");
        check(SeminarDemo.canonical(Map.of("z","a b","a","x")).equals("a=x&z=a+b"),"canonical ordering and encoding");
    }
}
