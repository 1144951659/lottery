package com.npc.lottery.user.action;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts2.ServletActionContext;

import com.alibaba.fastjson.JSONObject;
import com.npc.lottery.boss.entity.ShopsInfo;
import com.npc.lottery.common.Constant;
import com.npc.lottery.common.action.BaseAction;
import com.npc.lottery.sysmge.entity.LoginLogInfo;
import com.npc.lottery.sysmge.entity.ManagerUser;
import com.npc.lottery.sysmge.logic.interf.ILoginLogInfoLogic;
import com.npc.lottery.sysmge.logic.interf.ILoginManagerLogic;
import com.npc.lottery.sysmge.logic.interf.IMonitorLogic;

public class LoginAction extends BaseAction {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7049940540503451901L;

	private static Logger log = Logger.getLogger(LoginAction.class);

	private ILoginManagerLogic loginManagerLogic = null;

	private ILoginLogInfoLogic loginLogInfoLogic = null;

	private IMonitorLogic monitorLogic;

	public void setLoginManagerLogic(ILoginManagerLogic loginManagerLogic) {
		this.loginManagerLogic = loginManagerLogic;
	}

	public void setLoginLogInfoLogic(ILoginLogInfoLogic loginLogInfoLogic) {
		this.loginLogInfoLogic = loginLogInfoLogic;
	}

	private String userID;

	private String userName;

	private String userPwd;

	private String safetyCode;

	private String code;

	public String execute() throws Exception {
		log.info("execute");
		return "success";
	}

	/**
	 * 管理登陆
	 * 
	 * @return
	 * @throws Exception
	 */
	public String loginManager() throws Exception {

		HttpServletRequest request = ServletActionContext.getRequest();
		HttpSession session = request.getSession();
		/*
		//获取验证码
		String code=this.getRequest().getParameter("code");
		if(null == code){
			log.error("验证码为空，登陆失败！");
			return "codeError";
		}
		// 读取session中验证码
		String codeNO = (String) session.getAttribute(Constant.LOGIN_CODE);
		if (StringUtils.isNotEmpty(codeNO) && !codeNO.equals(code)) {
			log.error("验证码错误，登陆失败！");
			return "codeError";  //验证码校验错误
		} */
		
		String shopsCode = this.getSafetyCodeFromUrl();
		ShopsInfo shopsInfoEntity = null;
		try {
			shopsInfoEntity = loginManagerLogic.findShopsCode(shopsCode);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (null == shopsInfoEntity || shopsInfoEntity.getShopsCode().trim().length() != 4) {
			// 用户不存在
			log.error("未使用安全码登陆，登陆失败！");
			return "safetyCodeUnLogin";
		}

		// 判断该商铺是否可用
		String verifyShop = loginManagerLogic.verifyShop(shopsCode);
		if (Constant.SHOP_FREEZE.equalsIgnoreCase(verifyShop)) {
			return "shopFreeze";
		} else if (Constant.SHOP_CLOSE.equalsIgnoreCase(verifyShop)) {
			return "shopClose";
		}

		// 登录
		ManagerUser user = loginManagerLogic.verifyLogin(shopsInfoEntity.getShopsCode(), userName, userPwd);

		//如果是总管用户登录的,提示用户不存在
		if ("1".equals(user.getUserType())) {
			// 用户不存在
			log.error("用户不存在，登陆失败！");

			return "userUnexist";
		}
		// 判断是否登录成功，冻结也可以登录（但限制功能使用）
		if (user.isLogin() || ManagerUser.LOGIN_STATE_FAILURE_USER_FREEZE == user.getLoginState()) {
			// 保存安全码
			user.setShopsInfo(shopsInfoEntity);
			// 清除session中的安全码
			// session.removeAttribute(Constant.SAFETY_INFO_IN_SESSION);

			// 记录登陆IP
			String userIP = this.getClientIpAddr();
			user.setLoginIp(userIP);

			// 将登录信息保存到 session 中
			session.setAttribute(Constant.MANAGER_LOGIN_INFO_IN_SESSION, user);

			// 保存登陆用户类型到 session 中
			session.setAttribute(Constant.USER_TYPE_LOGIN_IN_SESSION_MANGER, user.getUserType());

			// 将安全码信息保存到 session 中
			session.setAttribute(Constant.SAFETY_INFO_IN_SESSION, shopsInfoEntity);

			log.error("【" + user.getAccount() + "（" + user.getChsName() + "）登录成功！】");

			// 初始化用户相关数据 TODO 根据返回值，处理相关内容
			loginManagerLogic.initResource(user);
		} else if (ManagerUser.LOGIN_STATE_FAILURE_USER_INEXIST == user.getLoginState()) {
			// 用户不存在
			log.error("用户不存在，登陆失败！");

			return "userUnexist";
		} else if (ManagerUser.LOGIN_STATE_FAILURE_PWD_INCORRECT == user.getLoginState()) {
			// 密码校验错误
			log.error("用户密码错误，登陆失败！");

			return "pwdIncorrect";
		} else if (ManagerUser.LOGIN_STATE_FAILURE_SAFETYCODE_ERROR == user.getLoginState()) {
			// 安全码校验错误
			log.error("用户与安全码不匹配，登陆失败！");
			return "safetyCodeError";
		} else if (ManagerUser.LOGIN_STATE_FAILURE_USER_FORBID == user.getLoginState()) {
			// 用户已被禁用
			log.error("用户已被禁用，登陆失败！");
			return "userForbid";
		}

		// TODO 登陆日志，填充其他更完善的信息
		this.logLoginInfo(user.getID(), user.getAccount(), user.getUserType());

		if (ManagerUser.LOGIN_STATE_FAILURE_USER_FREEZE == user.getLoginState()) {
			// 用户已被冻结
			log.error("用户已被冻结，登陆成功！");
			return "userFreeze";
		}

		return "success";
	}

	/**
	 * 管理登出
	 * 
	 * @return
	 * @throws Exception
	 */
	public String logoutManager() throws Exception {

		HttpServletRequest request = ServletActionContext.getRequest();
		HttpSession session = request.getSession();
		if (null != session) {
			ManagerUser user = (ManagerUser) session.getAttribute(Constant.MANAGER_LOGIN_INFO_IN_SESSION);
			if (null != user) {
				log.error("【" + user.getAccount() + "（" + user.getChsName() + "）退出！】");
				// modify by peter
				session.setAttribute(Constant.MANAGER_LOGIN_INFO_IN_SESSION, null);
			}

			request.setAttribute("loginType", "manager");
		}

		return "success";
	}

	/**
	 * 管理登录失败
	 * 
	 * @return
	 * @throws Exception
	 */
	public String loginFailureManager() throws Exception {

		String ERROR_TYPE = request.getParameter("ERROR_TYPE");

		System.out.println("ERROR_TYPE = " + ERROR_TYPE);

		if("codeError".equalsIgnoreCase(ERROR_TYPE)){
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "验证码错误！");
		}else if ("shopFreeze".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN, "true");
			request.setAttribute(Constant.INFO_PAGE_RETURN_URL, "/");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "商铺已被冻结，登陆失败！");
		} else if ("shopClose".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN, "true");
			request.setAttribute(Constant.INFO_PAGE_RETURN_URL, "/");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "商铺已关闭，登陆失败！");
		}else if ("userUnexist".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "'抱歉！你輸入帳號或密碼錯誤，請查正后重新登錄。'");
		} else if ("pwdIncorrect".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "'抱歉！你輸入帳號或密碼錯誤，請查正后重新登錄。'");
		} else if ("safetyCodeUnexist".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "安全码不存在，请与系统管理员联系！");
		} else if ("safetyCodeUnLogin".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN, "true");
			request.setAttribute(Constant.INFO_PAGE_RETURN_URL, "/");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "请使用安全码登陆，安全码可通过系统管理员处获取！");
		} else if ("safetyCodeError".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "用户与安全码不匹配，登陆失败！");
		} else if ("shopFreeze".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN, "true");
			request.setAttribute(Constant.INFO_PAGE_RETURN_URL, "/");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "安全码已被冻结，登陆失败！");
		} else if ("shopClose".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN, "true");
			request.setAttribute(Constant.INFO_PAGE_RETURN_URL, "/");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "安全码已关闭，登陆失败！");
		} else if ("userForbid".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "'抱歉！您的帳戶已被禁止使用，請和您的上線聯繫。'");
		} else if ("userFreeze".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "'抱歉！您的帳戶已被凍結使用，請和您的上線聯繫。'");
			request.setAttribute("userState", "userFreeze"); // 用户被冻结后是可以登录的，但限制部份功能
		} else if ("reLogin".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "'您已被强制登出！" + "您可能因以下其一原因被登出：" + "1、系統拿出。"
			        + "2、您的帳戶在多個地方登錄。" + "請從新登錄。如有任何疑問，請聯系上線或客服中心。'");
		} else if ("userAlreadyLoginLocal".equalsIgnoreCase(ERROR_TYPE)) {
			// 设置返回页面提示信息
			request.setAttribute(Constant.INFO_PAGE_TYPE_RETURN_SIMPLE, "true");
			request.setAttribute(Constant.INFO_PAGE_MESSAGE, "'抱歉！当前用户： " + request.getParameter("userName")
			        + " 已经登录,请退出或者新建会话再登录'");
		}

		return "failure";
	}

	public String ajaxCheckCode() {
		HttpSession session = request.getSession(true);

		// 登陆页面传过来的验证码
		String codeNO = (String) session.getAttribute(Constant.LOGIN_CODE);

		String checkResult = "false";
		if (StringUtils.isNotEmpty(codeNO) && codeNO.equals(code)) {
			checkResult = "true";// 校验成功
		} else {
			checkResult = "false";// 校验失败
		}

		Map<String, String> map = new HashMap<String, String>();
		map.put("checkResult", checkResult);

		try {
			HttpServletResponse response = ServletActionContext.getResponse();
			response.setContentType("text/html" + ";charset=UTF-8");
			response.setHeader("Pragma", "No-cache");
			response.setHeader("Cache-Control", "no-cache");
			response.setDateHeader("Expires", 0);
			response.getWriter().write(JSONObject.toJSONString(map));
			response.getWriter().flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (null != session && "true".equals(checkResult)) {
				//session.removeAttribute(Constant.LOGIN_CODE);
				session.invalidate();
			}
		}
		return null;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}

	public String getUserPwd() {
		return userPwd;
	}

	public void setUserPwd(String userPwd) {
		this.userPwd = userPwd;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getSafetyCode() {
		return safetyCode;
	}

	public void setSafetyCode(String safetyCode) {
		this.safetyCode = safetyCode;
	}

	public IMonitorLogic getMonitorLogic() {
		return monitorLogic;
	}

	public void setMonitorLogic(IMonitorLogic monitorLogic) {
		this.monitorLogic = monitorLogic;
	}

	private void logLoginInfo(long userID, String account, String userType) {
		LoginLogInfo logLogInfo = new LoginLogInfo();
		logLogInfo.setUserId(userID);// 用户ID
		logLogInfo.setAccount(account);// 用户账号
		String userIP = this.getClientIpAddr();

		logLogInfo.setLoginIp(userIP);// 登陆IP
		logLogInfo.setUserType(userType);// 用户类型
		logLogInfo.setLoginDate(new Date(System.currentTimeMillis()));// 登陆时间
		loginLogInfoLogic.saveLoginLogInfo(logLogInfo);
	}

	/**
	 * 如果通过了多级反向代理的话，X-Forwarded-For的值并不止一个，而是一串IP值， 那么真
	 * 正的用户端的真实IP则是取X-Forwarded-For中第一个非unknown的有效IP字符串。
	 * 
	 * @param request
	 *            请求对象
	 * @return 真实IP
	 */
	private String getClientIpAddr() {
		String ip = "";
		if (request.getHeader("REQ_REAL_IP") != null) {
			ip = request.getHeader("REQ_REAL_IP");
		} else {
			ip = request.getHeader("x-forwarded-for");
			if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
				ip = request.getHeader("Proxy-Client-IP");
			}
			if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
				ip = request.getHeader("WL-Proxy-Client-IP");
			}
			if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
				ip = request.getRemoteAddr();
			}
		}
		return ip;
	}
}
