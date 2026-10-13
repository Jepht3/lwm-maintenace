<?php
/**
 * Mises à jour de l'extension elle-même depuis les versions publiées sur GitHub.
 *
 * Lit le fichier latest.json joint à la dernière version publiée et l'injecte dans
 * le système de mises à jour de WordPress : l'extension apparaît dans
 * Extensions › Mises à jour (et dans l'application) comme n'importe quelle autre.
 *
 * @package RemoteUpdater
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class WPRU_Self_Update {

	const MANIFEST = 'https://github.com/Jepht3/lwm-maintenace/releases/latest/download/latest.json';
	const CACHE    = 'wpru_latest_release';
	const SLUG     = 'remote-updater';

	public static function init() {
		add_filter( 'pre_set_site_transient_update_plugins', array( __CLASS__, 'inject' ) );
		add_filter( 'plugins_api', array( __CLASS__, 'info' ), 20, 3 );
		add_action( 'upgrader_process_complete', array( __CLASS__, 'flush' ), 10, 0 );
		// « Rechercher les mises à jour » (WordPress ou l'app) relit aussi GitHub.
		add_action( 'delete_site_transient_update_plugins', array( __CLASS__, 'flush' ) );
	}

	/**
	 * Manifeste de la dernière version : { version, zip_url, requires, requires_php, tested, changelog[] }.
	 *
	 * @return array|null
	 */
	public static function latest() {
		$cached = get_site_transient( self::CACHE );
		if ( is_array( $cached ) ) {
			return empty( $cached['version'] ) ? null : $cached;
		}

		$res  = wp_remote_get( self::MANIFEST, array( 'timeout' => 10 ) );
		$data = null;
		if ( ! is_wp_error( $res ) && 200 === wp_remote_retrieve_response_code( $res ) ) {
			$json = json_decode( wp_remote_retrieve_body( $res ), true );
			if ( is_array( $json ) && ! empty( $json['version'] ) && ! empty( $json['zip_url'] ) ) {
				$data = $json;
			}
		}

		// Un échec est mis en cache moins longtemps pour réessayer vite.
		set_site_transient( self::CACHE, $data ? $data : array(), $data ? 3 * HOUR_IN_SECONDS : 30 * MINUTE_IN_SECONDS );
		return $data;
	}

	public static function flush() {
		delete_site_transient( self::CACHE );
	}

	private static function item( array $m ) {
		return (object) array(
			'id'           => self::SLUG,
			'slug'         => self::SLUG,
			'plugin'       => plugin_basename( WPRU_FILE ),
			'new_version'  => $m['version'],
			'url'          => 'https://lewebmax.com/remote-updater/',
			'package'      => $m['zip_url'],
			'requires'     => isset( $m['requires'] ) ? $m['requires'] : '5.8',
			'requires_php' => isset( $m['requires_php'] ) ? $m['requires_php'] : '7.4',
			'tested'       => isset( $m['tested'] ) ? $m['tested'] : '',
			'icons'        => array(),
			'banners'      => array(),
		);
	}

	public static function inject( $transient ) {
		if ( ! is_object( $transient ) ) {
			return $transient;
		}
		$m = self::latest();
		if ( ! $m ) {
			return $transient;
		}
		$file = plugin_basename( WPRU_FILE );
		$item = self::item( $m );
		if ( version_compare( $m['version'], WPRU_VERSION, '>' ) ) {
			$transient->response[ $file ] = $item;
			unset( $transient->no_update[ $file ] );
		} else {
			$item->new_version             = WPRU_VERSION;
			$transient->no_update[ $file ] = $item;
		}
		return $transient;
	}

	/** Fenêtre « Afficher les détails » de l'écran Extensions. */
	public static function info( $result, $action, $args ) {
		if ( 'plugin_information' !== $action || empty( $args->slug ) || self::SLUG !== $args->slug ) {
			return $result;
		}
		$m = self::latest();
		if ( ! $m ) {
			return $result;
		}
		$changelog = '';
		foreach ( (array) ( isset( $m['changelog'] ) ? $m['changelog'] : array() ) as $line ) {
			$changelog .= '<li>' . esc_html( $line ) . '</li>';
		}
		return (object) array(
			'name'          => 'Remote Updater',
			'slug'          => self::SLUG,
			'version'       => $m['version'],
			'author'        => 'Jephte',
			'homepage'      => 'https://lewebmax.com/remote-updater/',
			'requires'      => isset( $m['requires'] ) ? $m['requires'] : '5.8',
			'requires_php'  => isset( $m['requires_php'] ) ? $m['requires_php'] : '7.4',
			'tested'        => isset( $m['tested'] ) ? $m['tested'] : '',
			'last_updated'  => isset( $m['published'] ) ? $m['published'] : '',
			'download_link' => $m['zip_url'],
			'sections'      => array(
				'description' => '<p>Connectez ce site à l\'application Android « Remote Updater » pour gérer ses mises à jour à distance.</p>',
				'changelog'   => '<h4>' . esc_html( $m['version'] ) . '</h4><ul>' . $changelog . '</ul>',
			),
		);
	}
}
