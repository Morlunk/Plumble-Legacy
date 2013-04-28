package com.morlunk.mumbleclient.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import android.os.Environment;

/**
 * A static convenience class for the creation and retrieval of Plumble certificates.
 * @author morlunk
 */
public class PlumbleCertificateManager {

	private static final String PLUMBLE_CERTIFICATE_FOLDER = "Plumble";
	private static final String PLUMBLE_CERTIFICATE_FORMAT = "plumble-%d.p12";
	private static final String PLUMBLE_CERTIFICATE_EXTENSION = "p12";
	
	private static final String issuer = "CN=Plumble User";
	private static final Integer YEARS_VALID = 20;
	
	/**
	 * Generates a new X.509 passwordless certificate in PKCS12 format for connection to a Mumble server.
	 * This certificate is stored in the {@value #PLUMBLE_CERTIFICATE_FOLDER} folder on the external storage, in the format {@value #PLUMBLE_CERTIFICATE_FORMAT} where the timestamp is substituted in.
	 * @return The path of the generated certificate if the operation was a success. Otherwise, null.
	 */
	public static File generateCertificate() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException, IOException {
		File certificateDirectory = getCertificateDirectory();

		String certificateName = String.format(Locale.US, PLUMBLE_CERTIFICATE_FORMAT, System.currentTimeMillis()/1000L);
		File certificateFile = new File(certificateDirectory, certificateName);
		createCertificate(certificateFile);
		return certificateFile;
	}
	
	/**
	 * Returns a list of certificates in the {@value #PLUMBLE_CERTIFICATE_FOLDER} folder on external storage, ending with {@value #PLUMBLE_CERTIFICATE_EXTENSION}.
	 * @return A list of {@link File} objects containing certificates.
	 */
	public static List<File> getAvailableCertificates() {
		File certificateDirectory = getCertificateDirectory();
		
		File[] p12Files = certificateDirectory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(PLUMBLE_CERTIFICATE_EXTENSION);
			}
		});
		
		return Arrays.asList(p12Files);
	}
	
	public static X509Certificate createCertificate(File path) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException, IOException {
		BouncyCastleProvider provider = new BouncyCastleProvider(); // Use SpongyCastle provider, supports creating X509 certs
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048, new SecureRandom());
		
		KeyPair keyPair = generator.generateKeyPair();
		
		SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
		ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA").setProvider(provider).build(keyPair.getPrivate());
		
		Date startDate = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(startDate);
		calendar.add(Calendar.YEAR, YEARS_VALID);
	    Date endDate = calendar.getTime();
		
		X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(new X500Name(issuer), 
				BigInteger.ONE, 
				startDate, endDate, new X500Name(issuer), 
				publicKeyInfo);
		
		X509CertificateHolder certificateHolder = certBuilder.build(signer);
		
		X509Certificate certificate = new JcaX509CertificateConverter().setProvider(provider).getCertificate(certificateHolder);
		
		KeyStore keyStore = KeyStore.getInstance("PKCS12", provider);
		keyStore.load(null, null);
		keyStore.setKeyEntry("Plumble Key", keyPair.getPrivate(), null, new X509Certificate[] { certificate });
		
		keyStore.store(new FileOutputStream(path), "".toCharArray());
		
		return certificate;
	}
	
	/**
	 * Returns the certificate directory, {@value #PLUMBLE_CERTIFICATE_FOLDER}, on external storage.
	 * Will create if does not exist, and throw an assert if the external storage is not mounted.
	 * @return The {@link File} object of the directory.
	 */
	public static File getCertificateDirectory() {
		assert Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
		File certificateDirectory = new File(Environment.getExternalStorageDirectory(), PLUMBLE_CERTIFICATE_FOLDER);
		if(!certificateDirectory.exists())
			certificateDirectory.mkdir();
		return certificateDirectory;
	}
}
