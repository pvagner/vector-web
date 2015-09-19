/*
Copyright 2015 OpenMarket Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

'use strict';

var React = require('react');

var MatrixClientPeg = require("../../../../src/MatrixClientPeg");
var MessageComposerController = require("../../../../src/controllers/molecules/MessageComposer");
var ContentMessages = require("../../../../src/ContentMessages");

var ComponentBroker = require('../../../../src/ComponentBroker');
var MemberAvatar = ComponentBroker.get('atoms/MemberAvatar');

module.exports = React.createClass({
    displayName: 'MessageComposer',
    mixins: [MessageComposerController],

    onUploadClick: function(ev) {
        this.refs.uploadInput.getDOMNode().click();
    },

    onUploadFileSelected: function(ev) {
        var files = ev.target.files;
        // MessageComposer shouldn't have to rely on it's parent passing in a callback to upload a file
        if (files && files.length > 0) {
            this.props.uploadFile(files[0]);
        }
        this.refs.uploadInput.getDOMNode().value = null;
    },

    onButtonsKeyup: function(event) {
        var KEY_ENTER = 13;
        var KEY_SPACE = 32;

        switch (event.which) {
            case KEY_ENTER:
            case KEY_SPACE: {
                this.refs.uploadInput.getDOMNode().click();
                //event.stopPropagation();
                return false;
            }
        }
        return true;
    },

    render: function() {
        var me = this.props.room.getMember(MatrixClientPeg.get().credentials.userId);
        var uploadInputStyle = {display: 'none'};
        return (
            <div className="mx_MessageComposer">
                <div className="mx_MessageComposer_wrapper">
                    <div className="mx_MessageComposer_row">
                        <div className="mx_MessageComposer_avatar">
                            <MemberAvatar member={me} />
                        </div>
                        <div className="mx_MessageComposer_input">
                            <textarea ref="textarea" onKeyDown={this.onKeyDown} placeholder="Type a message" />
                        </div>
                        <div className="mx_MessageComposer_upload" role="button" tabIndex="0" onClick={this.onUploadClick} onKeyUp={this.onButtonsKeyup}>
                            <img src="img/upload.png" width="32" height="32" alt="Browse for a file..."/>
                            <input type="file" style={uploadInputStyle} ref="uploadInput" onChange={this.onUploadFileSelected} />
                        </div>
                    </div>
                </div>
            </div>
        );
    },
});

