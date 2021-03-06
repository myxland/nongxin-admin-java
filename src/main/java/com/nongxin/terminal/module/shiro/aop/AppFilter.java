package com.nongxin.terminal.module.shiro.aop;

import com.nongxin.terminal.entity.system.Role;
import com.nongxin.terminal.entity.system.User;
import com.nongxin.terminal.module.shiro.AppToken;
import com.nongxin.terminal.service.system.RoleService;
import com.nongxin.terminal.util.ResponseError;
import com.nongxin.terminal.util.enumUtil.system.RoleTypeEnum;
import com.nongxin.terminal.vo.constant.DefContants;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author Scott
 * @create 2018-07-12 15:56
 * @desc   鉴权登录拦截器
 **/
@Slf4j
public class AppFilter extends BasicHttpAuthenticationFilter {

	//private static final Logger LOGGER = LoggerFactory.getLogger(AppFilter.class);

	private RoleService roleService;



	/**
	 * 执行登录认证
	 *
	 * @param request
	 * @param response
	 * @param mappedValue
	 * @return
	 */
	@Override
	protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
		//LOGGER.info("isAccessAllowed：jwtfile");
		String url = getPathWithinApplication(request);
		//LOGGER.info("当前用户正在访问："+ url);
		try {
			this.executeLogin(request, response);
		} catch (Exception e) {
			//throw new AuthenticationException("Token失效，请重新登录", e);
			e = new AuthenticationException("Token失效，请重新登录");
			String msg = e.getMessage();
			ResponseError.response500(response, msg,HttpStatus.INTERNAL_SERVER_ERROR);
			return false;
		}
		try {
			HttpServletRequest req = (HttpServletRequest)request;
			HttpServletResponse resp = (HttpServletResponse)response;
			ServletContext sc = req.getSession().getServletContext();
			WebApplicationContext wacxt = WebApplicationContextUtils.getWebApplicationContext(sc);
			if(wacxt != null && wacxt.getBean("RoleService") != null && roleService == null) {
				roleService = (RoleService) wacxt.getBean("RoleService");
			}
			Subject subject = getSubject(request,response);
			PrincipalCollection principals = subject.getPrincipals();
			User sysUser = (User) principals.getPrimaryPrincipal();
			List<Role> roleList = roleService.getRoleByUid(sysUser.getUid());
			boolean isAdmin = false;
			for(Role r:roleList){
				if(RoleTypeEnum.ADMIN==r.getType()){
					isAdmin = true;
					break;
				}
			}
			if(isAdmin){
				//LOGGER.info("isAdmin all resources can do");
				return true;
			}else{
				return subject.isPermitted(url);
			}
		} catch (Exception e) {
			throw new AuthenticationException("鉴权失败，请重新登录", e);
		}
	}

	/**
	 *	登录验证
	 */
	@Override
	protected boolean executeLogin(ServletRequest request, ServletResponse response) throws Exception {
		HttpServletRequest httpServletRequest = (HttpServletRequest) request;
		String token = httpServletRequest.getHeader(DefContants.X_ACCESS_TOKEN);

		//LOGGER.info("executeLogin 执行登陆");

		AppToken appToken = new AppToken(token);
		// 提交给realm进行登入，如果错误他会抛出异常并被捕获
		getSubject(request, response).login(appToken);
		// 如果没有抛出异常则代表登入成功，返回true
		return true;
	}


	@Override
	protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
		HttpServletRequest httpServletRequest =(HttpServletRequest) request;
		HttpServletResponse httpServletResponse = (HttpServletResponse) response;
		//LOGGER.info(response.toString());
		httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		//LOGGER.info("onAccessDenied:"+getPathWithinApplication(httpServletRequest));
		return false;
	}



	/**
	 * 对跨域提供支持
	 */
	@Override
	protected boolean preHandle(ServletRequest request, ServletResponse response) throws Exception {
		HttpServletRequest httpServletRequest = (HttpServletRequest) request;
		HttpServletResponse httpServletResponse = (HttpServletResponse) response;
		//httpServletResponse.setHeader("Access-Control-Allow-Origin", httpServletRequest.getHeader("Origin"));
		//httpServletResponse.setHeader("Access-Control-Allow-Headers", httpServletRequest.getHeader("Access-Control-Request-Headers"));
		httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
		httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS,PUT,DELETE");
		httpServletResponse.setHeader("Access-Control-Allow-Headers", "Origin, No-Cache, X-Requested-With, If-Modified-Since, Pragma, Last-Modified, Cache-Control, Expires, Content-Type, X-E4M-With,userId,token");

		// 跨域时会首先发送一个option请求，这里我们给option请求直接返回正常状态
		if (httpServletRequest.getMethod().equals(RequestMethod.OPTIONS.name())) {
			httpServletResponse.setStatus(HttpStatus.OK.value());
			return false;
		}
		return super.preHandle(request, response);
	}
}
