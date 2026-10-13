<?php
/**
 * Routes REST utilisées par l'application Android (namespace wpru/v1).
 *
 * GET  /status                 Infos du site + nombre de mises à jour
 * GET  /updates?refresh=1      Tout : cœur, extensions, thèmes, traductions
 * POST /update/plugins         { "plugins": ["dossier/fichier.php"] } (vide = toutes)
 * POST /update/themes          { "themes": ["slug"] } (vide = tous)
 * POST /update/core
 * POST /update/translations
 * POST /upload                multipart : type=plugin|theme, package=<fichier .zip>
 * POST /auto-update           { "type": "plugin|theme", "item": "...", "enabled": true }
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class WPRU_Api {

	const NS = 'wpru/v1';

	public static function register_routes() {
		$auth = array( 'WPRU_Auth', 'authenticate' );

		register_rest_route(
			self::NS,
			'/status',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( __CLASS__, 'status' ),
				'permission_callback' => $auth,
			)
		);

		register_rest_route(
			self::NS,
			'/updates',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( __CLASS__, 'updates' ),
				'permission_callback' => $auth,
				'args'                => array(
					'refresh' => array(
						'type'    => 'boolean',
						'default' => false,
					),
				),
			)
		);

		register_rest_route(
			self::NS,
			'/update/plugins',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'update_plugins' ),
				'permission_callback' => $auth,
				'args'                => array(
					'plugins' => array(
						'type'    => 'array',
						'items'   => array( 'type' => 'string' ),
						'default' => array(),
					),
				),
			)
		);

		register_rest_route(
			self::NS,
			'/update/themes',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'update_themes' ),
				'permission_callback' => $auth,
				'args'                => array(
					'themes' => array(
						'type'    => 'array',
						'items'   => array( 'type' => 'string' ),
						'default' => array(),
					),
				),
			)
		);

		register_rest_route(
			self::NS,
			'/update/core',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'update_core' ),
				'permission_callback' => $auth,
			)
		);

		register_rest_route(
			self::NS,
			'/update/translations',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'update_translations' ),
				'permission_callback' => $auth,
			)
		);

		register_rest_route(
			self::NS,
			'/upload',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'upload' ),
				'permission_callback' => $auth,
				'args'                => array(
					'type' => array(
						'type'     => 'string',
						'enum'     => array( 'plugin', 'theme' ),
						'required' => true,
					),
				),
			)
		);

		register_rest_route(
			self::NS,
			'/auto-update',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'auto_update' ),
				'permission_callback' => $auth,
				'args'                => array(
					'type'    => array(
						'type'     => 'string',
						'enum'     => array( 'plugin', 'theme' ),
						'required' => true,
					),
					'item'    => array(
						'type'     => 'string',
						'required' => true,
					),
					'enabled' => array(
						'type'     => 'boolean',
						'required' => true,
					),
				),
			)
		);
	}

	private static function respond( $data ) {
		if ( is_wp_error( $data ) ) {
			return $data;
		}
		$response = rest_ensure_response( $data );
		$response->header( 'Cache-Control', 'no-store' );
		return $response;
	}

	public static function status() {
		return self::respond( WPRU_Updates::site_info() );
	}

	public static function updates( WP_REST_Request $request ) {
		if ( $request->get_param( 'refresh' ) ) {
			WPRU_Updates::refresh();
		}

		return self::respond(
			array(
				'site'         => WPRU_Updates::site_info(),
				'core'         => WPRU_Updates::core(),
				'plugins'      => WPRU_Updates::plugins(),
				'themes'       => WPRU_Updates::themes(),
				'translations' => WPRU_Updates::translations(),
			)
		);
	}

	public static function update_plugins( WP_REST_Request $request ) {
		return self::respond( WPRU_Updates::update_plugins( (array) $request->get_param( 'plugins' ) ) );
	}

	public static function update_themes( WP_REST_Request $request ) {
		return self::respond( WPRU_Updates::update_themes( (array) $request->get_param( 'themes' ) ) );
	}

	public static function update_core() {
		return self::respond( WPRU_Updates::update_core() );
	}

	public static function update_translations() {
		return self::respond( WPRU_Updates::update_translations() );
	}

	public static function upload( WP_REST_Request $request ) {
		$files = $request->get_file_params();
		return self::respond(
			WPRU_Updates::install_package(
				$request->get_param( 'type' ),
				isset( $files['package'] ) ? $files['package'] : null
			)
		);
	}

	public static function auto_update( WP_REST_Request $request ) {
		return self::respond(
			WPRU_Updates::set_auto_update(
				$request->get_param( 'type' ),
				sanitize_text_field( $request->get_param( 'item' ) ),
				(bool) $request->get_param( 'enabled' )
			)
		);
	}
}
