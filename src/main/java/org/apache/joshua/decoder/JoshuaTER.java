package org.apache.joshua.decoder;

import ter.core.*;

import java.util.ArrayList;

public class JoshuaTER {
    String source;
    TerScorer calc;
    CostFunction costfunc;
    
    public JoshuaTER() {
	calc = new TerScorer();
	costfunc = new CostFunction();
    }
    
    public double calculate(String hyp) {
	return calc.TER(hyp,source,costfunc).score();
    }
    
    public void setSource(String source) {
	this.source = source;
	calc.setRefLen(source.length());
    }
}
