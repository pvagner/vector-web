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
var filesize = require('filesize');

var MImageTileController = require("../../../../src/controllers/molecules/MImageTile");

var MatrixClientPeg = require('../../../../src/MatrixClientPeg');
var Modal = require('../../../../src/Modal');
var ComponentBroker = require('../../../../src/ComponentBroker');

var ImageView = ComponentBroker.get("atoms/ImageView");

module.exports = React.createClass({
    displayName: 'MImageTile',
    mixins: [MImageTileController],

    thumbHeight: function(fullWidth, fullHeight, thumbWidth, thumbHeight) {
        if (!fullWidth || !fullHeight) {
            // Cannot calculate thumbnail height for image: missing w/h in metadata. We can't even
            // log this because it's spammy
            return undefined;
        }
        if (fullWidth < thumbWidth && fullHeight < thumbHeight) {
            // no scaling needs to be applied
            return fullHeight;
        }
        var widthMulti = thumbWidth / fullWidth;
        var heightMulti = thumbHeight / fullHeight;
        if (widthMulti < heightMulti) {
            // width is the dominant dimension so scaling will be fixed on that
            return Math.floor(widthMulti * fullHeight);
        }
        else {
            // height is the dominant dimension so scaling will be fixed on that
            return Math.floor(heightMulti * fullHeight);
        }
    },

    onClick: function(ev) {
        var ms = ev.getModifierState();
        if (ev.button == 0 && !ev.metaKey) {
            ev.preventDefault();
            var content = this.props.mxEvent.getContent();
            var httpUrl = MatrixClientPeg.get().mxcUrlToHttp(content.url);
            Modal.createDialog(ImageView, {
                src: httpUrl,
                width: content.info.w,
                height: content.info.h
            });
        }
    },

    render: function() {
        var content = this.props.mxEvent.getContent();
        var cli = MatrixClientPeg.get();

        var thumbHeight = null;
        if (content.info) thumbHeight = this.thumbHeight(content.info.w, content.info.h, 320, 240);

        var imgStyle = {};
        if (thumbHeight) imgStyle['height'] = thumbHeight;

        return (
            <span className="mx_MImageTile">
                <a href={cli.mxcUrlToHttp(content.url)} onClick={ this.onClick }>
                    <img className="mx_MImageTile_thumbnail" src={cli.mxcUrlToHttp(content.url, 320, 240)} alt={content.body} style={imgStyle} />
                </a>
                <div className="mx_MImageTile_download">
                    <a href={cli.mxcUrlToHttp(content.url)} target="_blank">
                        <img src="img/download.png" width="10" height="12"/>
                        Download {content.body} ({ filesize(content.info.size) })
                    </a>
                </div>
            </span>
        );
    },
});
