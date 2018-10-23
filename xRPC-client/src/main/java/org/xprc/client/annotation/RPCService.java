package org.xprc.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
@Documented
public @interface RPCService {
	// 服务名称
	public String serviceName() default "";

	// 服务权重
	public int weight() default 50;

	public String responsibilityName() default "system";

	public int connCount() default 1;

	public boolean isVIPService() default false;

	public boolean isSupportDegradeService() default false;

	public String degradeServicePath() default "";

	public String degradeServiceDesc() default "";
	
	public boolean isFlowController() default true;		    //是否单位时间限流
	
	public long maxCallCountInMinute() default 10000;

}
