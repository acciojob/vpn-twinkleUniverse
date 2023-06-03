package com.driver.services.impl;

import com.driver.model.*;
import com.driver.repository.ConnectionRepository;
import com.driver.repository.ServiceProviderRepository;
import com.driver.repository.UserRepository;
import com.driver.services.ConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConnectionServiceImpl implements ConnectionService {
    @Autowired
    UserRepository userRepository2;
    @Autowired
    ServiceProviderRepository serviceProviderRepository2;
    @Autowired
    ConnectionRepository connectionRepository2;

    @Override
    public User connect(int userId, String countryName) throws Exception{
        User user=userRepository2.findById(userId).get();

        //1. If the user is already connected to any service provider, throw "Already connected" exception.
        if(user.isConnected()){
            throw new Exception("Already connected");
        }

        //2. Else if the countryName corresponds to the original country of the user, do nothing.
        // This means that the user wants to connect to its original country, for which we do not require a connection.
        // Thus, return the user as it is.
        if(countryName.equalsIgnoreCase(user.getOriginalCountry().getCountryName().toString())){
            return  user;
        }

        //3. Else, the user should be subscribed under a serviceProvider having option to connect to the given country.
        //If the connection can not be made (As user does not have a serviceProvider or serviceProvider does not have given country,
        // throw "Unable to connect" exception.
        //Else, establish the connection where the maskedIp is "updatedCountryCode.serviceProviderId.userId" and return the updated user.
        // If multiple service providers allow you to connect to the country, use the service provider having smallest id.

//        if(user.getServiceProviderList().size()==0){//no service providers
//            throw new Exception("Unable to connect");
//        }
        ServiceProvider minIdServiceProvider=null;
        int minId =Integer.MAX_VALUE;
        String countryCode=null;

        for(ServiceProvider serviceProvider:user.getServiceProviderList()){
            for (Country country:serviceProvider.getCountryList()){
                if(countryName.equalsIgnoreCase(country.getCountryName().toString()) && serviceProvider.getId()<minId){
                    minIdServiceProvider=serviceProvider;
                    minId=serviceProvider.getId();
                    countryCode=country.getCode();
                }
            }

        }
        if (minIdServiceProvider==null){//no providers give connection to given country
            throw new Exception("Unable to connect");
        }

        Connection connection=new Connection();
        connection.setUser(user);
        connection.setServiceProvider(minIdServiceProvider);
//        connectionRepository2.save(connection);

        minIdServiceProvider.getConnectionList().add(connection);

        String maskedIP=countryCode+"."+minIdServiceProvider.getId()+"."+user.getId();
        user.setMaskedIp(maskedIP);
        user.setConnected(true);
        user.getConnectionList().add(connection);


        serviceProviderRepository2.save(minIdServiceProvider);

        //is it necessary? cascade might cause double entry
        userRepository2.save(user);

        return  user;
    }
    @Override
    public User disconnect(int userId) throws Exception {

        User user=userRepository2.findById(userId).get();

        //If the given user was not connected to a vpn, throw "Already disconnected" exception.
        if(!user.isConnected()){
            throw new Exception("Already disconnected");
        }

        //Else, disconnect from vpn, make masked Ip as null, update relevant attributes and return updated user.
        user.setConnected(false);
        user.setMaskedIp(null);

        userRepository2.save(user);

        return user;
    }
    @Override
    public User communicate(int senderId, int receiverId) throws Exception {
        User sender=userRepository2.findById(senderId).get();
        User receiver=userRepository2.findById(receiverId).get();

        String receiverCountryCode=null;
        //If the receiver is connected to a vpn, his current country is the one he is connected to.
        //If the receiver is not connected to vpn, his current country is his original country.
        if(receiver.isConnected()){
            receiverCountryCode=receiver.getMaskedIp().substring(0,3);
            //since maskedIp format is "updatedCountryCode.serviceProviderId.userId" first part gives updated country code
        }
        else{
            receiverCountryCode=receiver.getOriginalCountry().getCode();
        }

        //The sender is initially not connected to any vpn so his country code is original country code.
        String senderCountryCode=sender.getOriginalCountry().getCode();

        //If the sender's original country matches receiver's current country, we do not need to do anything as they can communicate.
        // Return the sender as it is.
        if(senderCountryCode.equals(receiverCountryCode)){
            return sender;
        }

        //if not same
        //If the sender's original country does not match receiver's current country, we need to connect the sender to a suitable vpn.
        // If there are multiple options, connect using the service provider having smallest id
        //If communication can not be established due to any reason, throw "Cannot establish communication" exception

        String receiverCountryName=getCountryNameStringForCode(receiverCountryCode);

        try {
            User updatedSender=connect(senderId, receiverCountryName);
//            userRepository2.save(updatedSender);
            return updatedSender;
        }
        catch (Exception e){
            throw new Exception("Cannot establish communication");
        }

    }

    private String getCountryNameStringForCode(String countryCode) {
        for(CountryName countryName:CountryName.values()){
            if(countryCode.equals(countryName.toCode())){
                return countryName.toString();
            }
        }
        return null;
    }


    private CountryName getCountryName(String countryName) throws Exception {
        if(countryName.equalsIgnoreCase("IND") ){
            return CountryName.IND;
        }
        else if(countryName.equalsIgnoreCase("UDA") ){
            return CountryName.USA;
        }
        else if(countryName.equalsIgnoreCase("AUS")){
            return CountryName.AUS;
        }
        else if(countryName.equalsIgnoreCase("CHI")){
            return CountryName.CHI;
        }
        else if (countryName.equalsIgnoreCase("JPN")){
            return CountryName.JPN;
        }
        else{
            throw new Exception("Country not found");
        }

    }
}
