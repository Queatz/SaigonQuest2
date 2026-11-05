config.devServer = Object.assign({}, config.devServer, {
    historyApiFallback: true,
    port: 3000,
    compress: false,
    proxy: [
        {
            context: ["/api", "/media"],
            target: "http://127.0.0.1:8080",
            timeout: 0,
            proxyTimeout: 0,
        },
    ],
});
