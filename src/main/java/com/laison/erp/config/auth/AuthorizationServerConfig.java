package com.laison.erp.config.auth;

import com.laison.erp.common.utils.SpringContextUtils;
import com.laison.erp.config.auth.exception.MyWebResponseExceptionTranslator;
import com.laison.erp.config.RedisAuthorizationCodeServices;
import com.laison.erp.model.common.LoginAppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenGranter;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeTokenGranter;
import org.springframework.security.oauth2.provider.implicit.ImplicitTokenGranter;
import org.springframework.security.oauth2.provider.refresh.RefreshTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.DefaultUserAuthenticationConverter;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;
import org.springframework.web.cors.CorsConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ?????????????????????
 *
 * @author ??????
 */
@Configuration
@EnableAuthorizationServer
@Order(Ordered.LOWEST_PRECEDENCE)
@DependsOn(value = {"redisConnectionFactory","laisonAuthenticationSuccessHandler"})
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    /**
     * ???????????????
     *
     * @see SecurityConfig ???authenticationManagerBean()
     */
    @Autowired
    private AuthenticationManager authenticationManager;
    
    
    @Autowired
    RedisConnectionFactory redisConnectionFactory;//????????????????????????????????????null  
    
    

//    @Autowired
//	private ClientDetailsService clientDetailsService;
    
//    @Autowired
//	private AuthorizationServerTokenServices authorizationServerTokenServices;  //????????????

	
	//RedisAutoConfiguration 
    //private int refreshTokenValiditySeconds = 60 * 60 * 24 * 30; // default 30 days.
	//private int accessTokenValiditySeconds = 60 * 60 * 12; // default 12 hours.
    /**
     * ??????jwt??????redis<br>
     * ??????redis
     */
    @Value("${access_token.store-jwt:false}")
    private boolean storeWithJwt;
    /**
     * ??????????????????json????????????????????????????????????<br>
     * ??????false
     */
    @Value("${access_token.add-userinfo:false}")
    private boolean addUserInfo;
    @Autowired
    private RedisAuthorizationCodeServices redisAuthorizationCodeServices;
    @Autowired
    private RedisClientDetailsService redisClientDetailsService;
    

    /**
     * ????????????
     */
    @Bean
    public TokenStore tokenStore() {
    	
        if (storeWithJwt) {
            return new JwtTokenStore(accessTokenConverter());
        }
        if(redisConnectionFactory==null) {
        	 redisConnectionFactory=  SpringContextUtils.getBean(RedisConnectionFactory.class);
        }
        
       // 
        RedisTokenStore redisTokenStore = new RedisTokenStore(redisConnectionFactory);
        // 2018.08.04??????,????????????username????????????access_token??????????????????
        redisTokenStore.setAuthenticationKeyGenerator(new RandomAuthenticationKeyGenerator());
        
        
        return redisTokenStore;
    }

    /**
     * ??????
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
    	//laisonAuthenticationSuccessHandler.setTokenGranter(endpoints);
    	//defaultTokenServices.setTokenStore(tokenStore());
        endpoints.authenticationManager(this.authenticationManager);
        endpoints.setClientDetailsService(redisClientDetailsService);
        Map<String, CorsConfiguration> corsConfigMap = new HashMap<>();
        CorsConfiguration config =new CorsConfiguration();
		config.setAllowCredentials(true); // ??????cookies??????
	    config.addAllowedOrigin("*");// #????????????????????????????????????URI???*??????????????????
	    config.addAllowedHeader("*");// #????????????????????????,*????????????
	    config.setMaxAge(18000L);// ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
	    config.addAllowedMethod("*");// ??????????????????????????????*??????????????????
	    corsConfigMap.put("*", config );
		endpoints.getFrameworkEndpointHandlerMapping().setCorsConfigurations(corsConfigMap );
        endpoints.tokenStore(tokenStore());
        endpoints.authorizationCodeServices(redisAuthorizationCodeServices);
        endpoints.exceptionTranslator(new MyWebResponseExceptionTranslator());
       
      
        
        AuthorizationServerTokenServices tokenServices = endpoints.getTokenServices();
        AuthorizationCodeServices authorizationCodeServices = endpoints.getAuthorizationCodeServices();
        ClientDetailsService clientDetails = endpoints.getClientDetailsService();
        OAuth2RequestFactory requestFactory = endpoints.getOAuth2RequestFactory();
		List<TokenGranter> tokenGranters = new ArrayList<TokenGranter>();
		
		tokenGranters.add(new AuthorizationCodeTokenGranter(tokenServices, authorizationCodeServices, clientDetails,requestFactory));
		tokenGranters.add(new RefreshTokenGranter(tokenServices, clientDetails, requestFactory));
		ImplicitTokenGranter implicit = new ImplicitTokenGranter(tokenServices, clientDetails, requestFactory);
		tokenGranters.add(implicit);
		tokenGranters.add(new ClientCredentialsTokenGranter(tokenServices, clientDetails, requestFactory));
		tokenGranters.add(new MyResourceOwnerPasswordTokenGranter(authenticationManager, tokenServices,clientDetails, requestFactory));
		
	      TokenGranter newtokenGranter =new TokenGranter() {
			private CompositeTokenGranter delegate;
	
			@Override
			public OAuth2AccessToken grant(String grantType, TokenRequest tokenRequest) {
				if (delegate == null) {
					delegate = new CompositeTokenGranter(tokenGranters);
				}
				return delegate.grant(grantType, tokenRequest);
			}
		};
        endpoints.tokenGranter(newtokenGranter);
        if (storeWithJwt) {
            endpoints.accessTokenConverter(accessTokenConverter());
        } else {
            // 2018.07.13 ??????????????????????????????????????????????????????
            endpoints.tokenEnhancer((accessToken, authentication) -> {
                addLoginUserInfo(accessToken, authentication);
                return accessToken;
            });
        }
       
    }

   

	/**
     * ????????????????????????????????????????????????json?????????<br>
     * ????????????access_token.add-userinfo??????<br>
     * 2018.07.13
     *
     * @param accessToken
     * @param authentication
     */
    private void addLoginUserInfo(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        if (!addUserInfo) {
            return;
        }

        if (accessToken instanceof DefaultOAuth2AccessToken) {
            DefaultOAuth2AccessToken defaultOAuth2AccessToken = (DefaultOAuth2AccessToken) accessToken;

            Authentication userAuthentication = authentication.getUserAuthentication();
            Object principal = userAuthentication.getPrincipal();
            if (principal instanceof LoginAppUser) {
                LoginAppUser loginUser = (LoginAppUser) principal;

                Map<String, Object> map = new HashMap<>(defaultOAuth2AccessToken.getAdditionalInformation()); // ??????????????????
                map.put("loginUser", loginUser); // ????????????????????????

                defaultOAuth2AccessToken.setAdditionalInformation(map);
            }
        }
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.allowFormAuthenticationForClients(); // ???????????????????????????
    }

//    @Autowired
//    private BCryptPasswordEncoder bCryptPasswordEncoder;
    /**
     * ?????????client???????????????oauth_client_details??????<br>
     * ?????????????????????redis
     *
     * @param clients
     * @throws Exception
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
//		clients.inMemory().withClient("system").secret(bCryptPasswordEncoder.encode("system"))
//				.authorizedGrantTypes("password", "authorization_code", "refresh_token").scopes("app")
//				.accessTokenValiditySeconds(3600);

//		clients.jdbc(dataSource);
        // 2018.06.06????????????????????????????????????redisClientDetailsService???????????????
        clients.withClientDetails(redisClientDetailsService);
        redisClientDetailsService.loadAllClientToCache();
    }

    @Autowired
    public UserDetailsService userDetailsService;
    /**
     * jwt??????key??????????????????<br>
     * ???????????????????????????????????????????????????????????????
     */
    @Value("${access_token.jwt-signing-key:lihua}")
    private String signingKey;

    /**
     * Jwt?????????????????????<br>
     * ??????access_token.store-jwt???true?????????
     *
     * @return accessTokenConverter
     */
    @Bean
    public JwtAccessTokenConverter accessTokenConverter() {
        JwtAccessTokenConverter jwtAccessTokenConverter = new JwtAccessTokenConverter() {
            @Override
            public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
                OAuth2AccessToken oAuth2AccessToken = super.enhance(accessToken, authentication);
                addLoginUserInfo(oAuth2AccessToken, authentication); // 2018.07.13 ??????????????????????????????????????????????????????
                return oAuth2AccessToken;
            }
        };
        DefaultAccessTokenConverter defaultAccessTokenConverter = (DefaultAccessTokenConverter) jwtAccessTokenConverter
                .getAccessTokenConverter();
        DefaultUserAuthenticationConverter userAuthenticationConverter = new DefaultUserAuthenticationConverter();
        userAuthenticationConverter.setUserDetailsService(userDetailsService);

        defaultAccessTokenConverter.setUserTokenConverter(userAuthenticationConverter);
        // 2018.06.29 ????????????????????????????????????????????????????????????????????????jwt?????????access_token???????????????
        jwtAccessTokenConverter.setSigningKey(signingKey);

        return jwtAccessTokenConverter;
    }

}
