
        return new Ext.form.FormPanel({
            baseCls: 'x-plain',
            labelWidth: 50,
            defaultType: 'textfield',
            url: ORYX.CONFIG.EXPORT_TO_APROMORE,
            items: [{
                fieldLabel: 'Process Name',
                hideLabel: false,
                xtype: 'textfield',
                id: 'APProcessName',
                allowBlank: false,
                value: this.customUnescape(this.getParameter(window.location.href, "processName"))
            
            }, {
                fieldLabel: 'Version',
                xtype: 'textfield',
                id: 'APProcessVersion',
                allowBlank: false,
                value: isSave?this.getNextVersion(this.customUnescape(this.getParameter(window.location.href, "processVersion"))):'0.1'
            
            }, {
                xtype: 'hidden',
                id: 'sessionCode',
                name: 'sessionCode',
                value: this.getParameter(window.location.href, "sessionCode")
            }, {
                xtype: 'hidden',
                id: 'isSave',
                name: 'isSave',
                value: isSave
            
            }, {
        });
	},